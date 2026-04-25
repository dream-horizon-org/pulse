'use strict';

var clientS3 = require('@aws-sdk/client-s3');
var middlewareEndpoint = require('@smithy/middleware-endpoint');
var smithyClient = require('@smithy/smithy-client');
var events = require('events');
var buffer = require('buffer');
var runtimeConfig = require('./runtimeConfig');
var stream = require('stream');

const byteLength = (input) => {
    if (input == null) {
        return 0;
    }
    if (typeof input === "string") {
        return buffer.Buffer.byteLength(input);
    }
    if (typeof input.byteLength === "number") {
        return input.byteLength;
    }
    else if (typeof input.length === "number") {
        return input.length;
    }
    else if (typeof input.size === "number") {
        return input.size;
    }
    else if (typeof input.start === "number" && typeof input.end === "number") {
        return input.end + 1 - input.start;
    }
    else if (runtimeConfig.runtimeConfig.isFileReadStream(input)) {
        try {
            return runtimeConfig.runtimeConfig.lstatSync(input.path).size;
        }
        catch (error) {
            return undefined;
        }
    }
    return undefined;
};

var BYTE_LENGTH_SOURCE;
(function (BYTE_LENGTH_SOURCE) {
    BYTE_LENGTH_SOURCE["EMPTY_INPUT"] = "a null or undefined Body";
    BYTE_LENGTH_SOURCE["CONTENT_LENGTH"] = "the ContentLength property of the params set by the caller";
    BYTE_LENGTH_SOURCE["STRING_LENGTH"] = "the encoded byte length of the Body string";
    BYTE_LENGTH_SOURCE["TYPED_ARRAY"] = "the byteLength of a typed byte array such as Uint8Array";
    BYTE_LENGTH_SOURCE["LENGTH"] = "the value of Body.length";
    BYTE_LENGTH_SOURCE["SIZE"] = "the value of Body.size";
    BYTE_LENGTH_SOURCE["START_END_DIFF"] = "the numeric difference between Body.start and Body.end";
    BYTE_LENGTH_SOURCE["LSTAT"] = "the size of the file given by Body.path on disk as reported by lstatSync";
})(BYTE_LENGTH_SOURCE || (BYTE_LENGTH_SOURCE = {}));
const byteLengthSource = (input, override) => {
    if (override != null) {
        return BYTE_LENGTH_SOURCE.CONTENT_LENGTH;
    }
    if (input == null) {
        return BYTE_LENGTH_SOURCE.EMPTY_INPUT;
    }
    if (typeof input === "string") {
        return BYTE_LENGTH_SOURCE.STRING_LENGTH;
    }
    if (typeof input.byteLength === "number") {
        return BYTE_LENGTH_SOURCE.TYPED_ARRAY;
    }
    else if (typeof input.length === "number") {
        return BYTE_LENGTH_SOURCE.LENGTH;
    }
    else if (typeof input.size === "number") {
        return BYTE_LENGTH_SOURCE.SIZE;
    }
    else if (typeof input.start === "number" && typeof input.end === "number") {
        return BYTE_LENGTH_SOURCE.START_END_DIFF;
    }
    else if (runtimeConfig.runtimeConfig.isFileReadStream(input)) {
        try {
            runtimeConfig.runtimeConfig.lstatSync(input.path).size;
            return BYTE_LENGTH_SOURCE.LSTAT;
        }
        catch (error) {
            return undefined;
        }
    }
    return undefined;
};

async function* getChunkStream(data, partSize, getNextData) {
    let partNumber = 1;
    const currentBuffer = { chunks: [], length: 0 };
    for await (const datum of getNextData(data)) {
        currentBuffer.chunks.push(datum);
        currentBuffer.length += datum.byteLength;
        while (currentBuffer.length > partSize) {
            const dataChunk = currentBuffer.chunks.length > 1 ? buffer.Buffer.concat(currentBuffer.chunks) : currentBuffer.chunks[0];
            yield {
                partNumber,
                data: dataChunk.subarray(0, partSize),
            };
            currentBuffer.chunks = [dataChunk.subarray(partSize)];
            currentBuffer.length = currentBuffer.chunks[0].byteLength;
            partNumber += 1;
        }
    }
    yield {
        partNumber,
        data: currentBuffer.chunks.length !== 1 ? buffer.Buffer.concat(currentBuffer.chunks) : currentBuffer.chunks[0],
        lastPart: true,
    };
}

async function* getChunkUint8Array(data, partSize) {
    let partNumber = 1;
    let startByte = 0;
    let endByte = partSize;
    while (endByte < data.byteLength) {
        yield {
            partNumber,
            data: data.subarray(startByte, endByte),
        };
        partNumber += 1;
        startByte = endByte;
        endByte = startByte + partSize;
    }
    yield {
        partNumber,
        data: data.subarray(startByte),
        lastPart: true,
    };
}

async function* getDataReadable(data) {
    for await (const chunk of data) {
        if (buffer.Buffer.isBuffer(chunk) || chunk instanceof Uint8Array) {
            yield chunk;
        }
        else {
            yield buffer.Buffer.from(chunk);
        }
    }
}

async function* getDataReadableStream(data) {
    const reader = data.getReader();
    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) {
                return;
            }
            if (buffer.Buffer.isBuffer(value) || value instanceof Uint8Array) {
                yield value;
            }
            else {
                yield buffer.Buffer.from(value);
            }
        }
    }
    catch (e) {
        throw e;
    }
    finally {
        reader.releaseLock();
    }
}

const getChunk = (data, partSize) => {
    if (data instanceof Uint8Array) {
        return getChunkUint8Array(data, partSize);
    }
    if (data instanceof stream.Readable) {
        return getChunkStream(data, partSize, getDataReadable);
    }
    if (data instanceof String || typeof data === "string") {
        return getChunkUint8Array(buffer.Buffer.from(data), partSize);
    }
    if (typeof data.stream === "function") {
        return getChunkStream(data.stream(), partSize, getDataReadableStream);
    }
    if (data instanceof ReadableStream) {
        return getChunkStream(data, partSize, getDataReadableStream);
    }
    throw new Error("Body Data is unsupported format, expected data to be one of: string | Uint8Array | Buffer | Readable | ReadableStream | Blob;.");
};

class Upload extends events.EventEmitter {
    static MIN_PART_SIZE = 1024 * 1024 * 5;
    MAX_PARTS = 10_000;
    queueSize = 4;
    partSize;
    leavePartsOnError = false;
    tags = [];
    client;
    params;
    totalBytes;
    totalBytesSource;
    bytesUploadedSoFar;
    abortController;
    concurrentUploaders = [];
    createMultiPartPromise;
    abortMultipartUploadCommand = null;
    uploadedParts = [];
    uploadEnqueuedPartsCount = 0;
    expectedPartsCount;
    uploadId;
    uploadEvent;
    isMultiPart = true;
    singleUploadResult;
    sent = false;
    constructor(options) {
        super();
        this.queueSize = options.queueSize || this.queueSize;
        this.leavePartsOnError = options.leavePartsOnError || this.leavePartsOnError;
        this.tags = options.tags || this.tags;
        this.client = options.client;
        this.params = options.params;
        if (!this.params) {
            throw new Error(`InputError: Upload requires params to be passed to upload.`);
        }
        this.totalBytes = this.params.ContentLength ?? byteLength(this.params.Body);
        this.totalBytesSource = byteLengthSource(this.params.Body, this.params.ContentLength);
        this.bytesUploadedSoFar = 0;
        this.abortController = options.abortController ?? new AbortController();
        this.partSize =
            options.partSize || Math.max(Upload.MIN_PART_SIZE, Math.floor((this.totalBytes || 0) / this.MAX_PARTS));
        if (this.totalBytes !== undefined) {
            this.expectedPartsCount = Math.ceil(this.totalBytes / this.partSize);
        }
        this.__validateInput();
    }
    async abort() {
        this.abortController.abort();
    }
    async done() {
        if (this.sent) {
            throw new Error("@aws-sdk/lib-storage: this instance of Upload has already executed .done(). Create a new instance.");
        }
        this.sent = true;
        return await Promise.race([this.__doMultipartUpload(), this.__abortTimeout(this.abortController.signal)]);
    }
    on(event, listener) {
        this.uploadEvent = event;
        return super.on(event, listener);
    }
    async __uploadUsingPut(dataPart) {
        this.isMultiPart = false;
        const params = { ...this.params, Body: dataPart.data };
        const clientConfig = this.client.config;
        const requestHandler = clientConfig.requestHandler;
        const eventEmitter = requestHandler instanceof events.EventEmitter ? requestHandler : null;
        const uploadEventListener = (event) => {
            this.bytesUploadedSoFar = event.loaded;
            this.totalBytes = event.total;
            this.__notifyProgress({
                loaded: this.bytesUploadedSoFar,
                total: this.totalBytes,
                part: dataPart.partNumber,
                Key: this.params.Key,
                Bucket: this.params.Bucket,
            });
        };
        if (eventEmitter !== null) {
            eventEmitter.on("xhr.upload.progress", uploadEventListener);
        }
        const resolved = await Promise.all([this.client.send(new clientS3.PutObjectCommand(params)), clientConfig?.endpoint?.()]);
        const putResult = resolved[0];
        let endpoint = resolved[1];
        if (!endpoint) {
            endpoint = middlewareEndpoint.toEndpointV1(await middlewareEndpoint.getEndpointFromInstructions(params, clientS3.PutObjectCommand, {
                ...clientConfig,
            }));
        }
        if (!endpoint) {
            throw new Error('Could not resolve endpoint from S3 "client.config.endpoint()" nor EndpointsV2.');
        }
        if (eventEmitter !== null) {
            eventEmitter.off("xhr.upload.progress", uploadEventListener);
        }
        const locationKey = this.params
            .Key.split("/")
            .map((segment) => smithyClient.extendedEncodeURIComponent(segment))
            .join("/");
        const locationBucket = smithyClient.extendedEncodeURIComponent(this.params.Bucket);
        const Location = (() => {
            const endpointHostnameIncludesBucket = endpoint.hostname.startsWith(`${locationBucket}.`);
            const forcePathStyle = this.client.config.forcePathStyle;
            const optionalPort = endpoint.port ? `:${endpoint.port}` : ``;
            if (forcePathStyle) {
                return `${endpoint.protocol}//${endpoint.hostname}${optionalPort}/${locationBucket}/${locationKey}`;
            }
            if (endpointHostnameIncludesBucket) {
                return `${endpoint.protocol}//${endpoint.hostname}${optionalPort}/${locationKey}`;
            }
            return `${endpoint.protocol}//${locationBucket}.${endpoint.hostname}${optionalPort}/${locationKey}`;
        })();
        this.singleUploadResult = {
            ...putResult,
            Bucket: this.params.Bucket,
            Key: this.params.Key,
            Location,
        };
        const totalSize = byteLength(dataPart.data);
        this.__notifyProgress({
            loaded: totalSize,
            total: totalSize,
            part: 1,
            Key: this.params.Key,
            Bucket: this.params.Bucket,
        });
    }
    async __createMultipartUpload() {
        const requestChecksumCalculation = await this.client.config.requestChecksumCalculation();
        if (!this.createMultiPartPromise) {
            const createCommandParams = { ...this.params, Body: undefined };
            if (requestChecksumCalculation === "WHEN_SUPPORTED") {
                createCommandParams.ChecksumAlgorithm = this.params.ChecksumAlgorithm || clientS3.ChecksumAlgorithm.CRC32;
            }
            this.createMultiPartPromise = this.client
                .send(new clientS3.CreateMultipartUploadCommand(createCommandParams))
                .then((createMpuResponse) => {
                this.abortMultipartUploadCommand = new clientS3.AbortMultipartUploadCommand({
                    Bucket: this.params.Bucket,
                    Key: this.params.Key,
                    UploadId: createMpuResponse.UploadId,
                });
                return createMpuResponse;
            });
        }
        return this.createMultiPartPromise;
    }
    async __doConcurrentUpload(dataFeeder) {
        for await (const dataPart of dataFeeder) {
            if (this.uploadEnqueuedPartsCount > this.MAX_PARTS) {
                throw new Error(`Exceeded ${this.MAX_PARTS} parts in multipart upload to Bucket: ${this.params.Bucket} Key: ${this.params.Key}.`);
            }
            if (this.abortController.signal.aborted) {
                return;
            }
            if (dataPart.partNumber === 1 && dataPart.lastPart) {
                return await this.__uploadUsingPut(dataPart);
            }
            if (!this.uploadId) {
                const { UploadId } = await this.__createMultipartUpload();
                this.uploadId = UploadId;
                if (this.abortController.signal.aborted) {
                    return;
                }
            }
            const partSize = byteLength(dataPart.data) || 0;
            const requestHandler = this.client.config.requestHandler;
            const eventEmitter = requestHandler instanceof events.EventEmitter ? requestHandler : null;
            let lastSeenBytes = 0;
            const uploadEventListener = (event, request) => {
                const requestPartSize = Number(request.query["partNumber"]) || -1;
                if (requestPartSize !== dataPart.partNumber) {
                    return;
                }
                if (event.total && partSize) {
                    this.bytesUploadedSoFar += event.loaded - lastSeenBytes;
                    lastSeenBytes = event.loaded;
                }
                this.__notifyProgress({
                    loaded: this.bytesUploadedSoFar,
                    total: this.totalBytes,
                    part: dataPart.partNumber,
                    Key: this.params.Key,
                    Bucket: this.params.Bucket,
                });
            };
            if (eventEmitter !== null) {
                eventEmitter.on("xhr.upload.progress", uploadEventListener);
            }
            this.uploadEnqueuedPartsCount += 1;
            this.__validateUploadPart(dataPart);
            const partResult = await this.client.send(new clientS3.UploadPartCommand({
                ...this.params,
                ContentLength: undefined,
                UploadId: this.uploadId,
                Body: dataPart.data,
                PartNumber: dataPart.partNumber,
            }));
            if (eventEmitter !== null) {
                eventEmitter.off("xhr.upload.progress", uploadEventListener);
            }
            if (this.abortController.signal.aborted) {
                return;
            }
            if (!partResult.ETag) {
                throw new Error(`Part ${dataPart.partNumber} is missing ETag in UploadPart response. Missing Bucket CORS configuration for ETag header?`);
            }
            this.uploadedParts.push({
                PartNumber: dataPart.partNumber,
                ETag: partResult.ETag,
                ...(partResult.ChecksumCRC32 && { ChecksumCRC32: partResult.ChecksumCRC32 }),
                ...(partResult.ChecksumCRC32C && { ChecksumCRC32C: partResult.ChecksumCRC32C }),
                ...(partResult.ChecksumSHA1 && { ChecksumSHA1: partResult.ChecksumSHA1 }),
                ...(partResult.ChecksumSHA256 && { ChecksumSHA256: partResult.ChecksumSHA256 }),
            });
            if (eventEmitter === null) {
                this.bytesUploadedSoFar += partSize;
            }
            this.__notifyProgress({
                loaded: this.bytesUploadedSoFar,
                total: this.totalBytes,
                part: dataPart.partNumber,
                Key: this.params.Key,
                Bucket: this.params.Bucket,
            });
        }
    }
    async __doMultipartUpload() {
        const dataFeeder = getChunk(this.params.Body, this.partSize);
        const concurrentUploaderFailures = [];
        for (let index = 0; index < this.queueSize; index++) {
            const currentUpload = this.__doConcurrentUpload(dataFeeder).catch((err) => {
                concurrentUploaderFailures.push(err);
            });
            this.concurrentUploaders.push(currentUpload);
        }
        await Promise.all(this.concurrentUploaders);
        if (concurrentUploaderFailures.length >= 1) {
            await this.markUploadAsAborted();
            throw concurrentUploaderFailures[0];
        }
        if (this.abortController.signal.aborted) {
            await this.markUploadAsAborted();
            throw Object.assign(new Error("Upload aborted."), { name: "AbortError" });
        }
        let result;
        if (this.isMultiPart) {
            const { expectedPartsCount, uploadedParts, totalBytes, totalBytesSource } = this;
            if (totalBytes !== undefined && expectedPartsCount !== undefined && uploadedParts.length !== expectedPartsCount) {
                throw new Error(`Expected ${expectedPartsCount} part(s) but uploaded ${uploadedParts.length} part(s).
The expected part count is based on the byte-count of the input.params.Body,
which was read from ${totalBytesSource} and is ${totalBytes}.
If this is not correct, provide an override value by setting a number
to input.params.ContentLength in bytes.
`);
            }
            this.uploadedParts.sort((a, b) => a.PartNumber - b.PartNumber);
            const uploadCompleteParams = {
                ...this.params,
                Body: undefined,
                UploadId: this.uploadId,
                MultipartUpload: {
                    Parts: this.uploadedParts,
                },
            };
            result = await this.client.send(new clientS3.CompleteMultipartUploadCommand(uploadCompleteParams));
            if (typeof result?.Location === "string" && result.Location.includes("%2F")) {
                result.Location = result.Location.replace(/%2F/g, "/");
            }
        }
        else {
            result = this.singleUploadResult;
        }
        this.abortMultipartUploadCommand = null;
        if (this.tags.length) {
            await this.client.send(new clientS3.PutObjectTaggingCommand({
                ...this.params,
                Tagging: {
                    TagSet: this.tags,
                },
            }));
        }
        return result;
    }
    async markUploadAsAborted() {
        if (this.uploadId && !this.leavePartsOnError && null !== this.abortMultipartUploadCommand) {
            await this.client.send(this.abortMultipartUploadCommand);
            this.abortMultipartUploadCommand = null;
        }
    }
    __notifyProgress(progress) {
        if (this.uploadEvent) {
            this.emit(this.uploadEvent, progress);
        }
    }
    async __abortTimeout(abortSignal) {
        return new Promise((resolve, reject) => {
            abortSignal.onabort = () => {
                const abortError = new Error("Upload aborted.");
                abortError.name = "AbortError";
                reject(abortError);
            };
        });
    }
    __validateUploadPart(dataPart) {
        const actualPartSize = byteLength(dataPart.data);
        if (actualPartSize === undefined) {
            throw new Error(`A dataPart was generated without a measurable data chunk size for part number ${dataPart.partNumber}`);
        }
        if (dataPart.partNumber === 1 && dataPart.lastPart) {
            return;
        }
        if (!dataPart.lastPart && actualPartSize !== this.partSize) {
            throw new Error(`The byte size for part number ${dataPart.partNumber}, size ${actualPartSize} does not match expected size ${this.partSize}`);
        }
    }
    __validateInput() {
        if (!this.client) {
            throw new Error(`InputError: Upload requires a AWS client to do uploads with.`);
        }
        if (this.partSize < Upload.MIN_PART_SIZE) {
            throw new Error(`EntityTooSmall: Your proposed upload part size [${this.partSize}] is smaller than the minimum allowed size [${Upload.MIN_PART_SIZE}] (5MB)`);
        }
        if (this.queueSize < 1) {
            throw new Error(`Queue size: Must have at least one uploading queue.`);
        }
    }
}

exports.Upload = Upload;
