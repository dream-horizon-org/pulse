"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getRuntimeConfig = void 0;
const httpAuthSchemes_1 = require("@aws-sdk/core/httpAuthSchemes");
const middleware_sdk_s3_1 = require("@aws-sdk/middleware-sdk-s3");
const signature_v4_multi_region_1 = require("@aws-sdk/signature-v4-multi-region");
const smithy_client_1 = require("@smithy/smithy-client");
const url_parser_1 = require("@smithy/url-parser");
const util_base64_1 = require("@smithy/util-base64");
const util_stream_1 = require("@smithy/util-stream");
const util_utf8_1 = require("@smithy/util-utf8");
const httpAuthSchemeProvider_1 = require("./auth/httpAuthSchemeProvider");
const endpointResolver_1 = require("./endpoint/endpointResolver");
const schemas_0_1 = require("./schemas/schemas_0");
const getRuntimeConfig = (config) => {
    return {
        apiVersion: "2006-03-01",
        base64Decoder: config?.base64Decoder ?? util_base64_1.fromBase64,
        base64Encoder: config?.base64Encoder ?? util_base64_1.toBase64,
        disableHostPrefix: config?.disableHostPrefix ?? false,
        endpointProvider: config?.endpointProvider ?? endpointResolver_1.defaultEndpointResolver,
        extensions: config?.extensions ?? [],
        getAwsChunkedEncodingStream: config?.getAwsChunkedEncodingStream ?? util_stream_1.getAwsChunkedEncodingStream,
        httpAuthSchemeProvider: config?.httpAuthSchemeProvider ?? httpAuthSchemeProvider_1.defaultS3HttpAuthSchemeProvider,
        httpAuthSchemes: config?.httpAuthSchemes ?? [
            {
                schemeId: "aws.auth#sigv4",
                identityProvider: (ipc) => ipc.getIdentityProvider("aws.auth#sigv4"),
                signer: new httpAuthSchemes_1.AwsSdkSigV4Signer(),
            },
            {
                schemeId: "aws.auth#sigv4a",
                identityProvider: (ipc) => ipc.getIdentityProvider("aws.auth#sigv4a"),
                signer: new httpAuthSchemes_1.AwsSdkSigV4ASigner(),
            },
        ],
        logger: config?.logger ?? new smithy_client_1.NoOpLogger(),
        protocol: config?.protocol ?? middleware_sdk_s3_1.S3RestXmlProtocol,
        protocolSettings: config?.protocolSettings ?? {
            defaultNamespace: "com.amazonaws.s3",
            errorTypeRegistries: schemas_0_1.errorTypeRegistries,
            xmlNamespace: "http://s3.amazonaws.com/doc/2006-03-01/",
            version: "2006-03-01",
            serviceTarget: "AmazonS3",
        },
        sdkStreamMixin: config?.sdkStreamMixin ?? util_stream_1.sdkStreamMixin,
        serviceId: config?.serviceId ?? "S3",
        signerConstructor: config?.signerConstructor ?? signature_v4_multi_region_1.SignatureV4MultiRegion,
        signingEscapePath: config?.signingEscapePath ?? false,
        urlParser: config?.urlParser ?? url_parser_1.parseUrl,
        useArnRegion: config?.useArnRegion ?? undefined,
        utf8Decoder: config?.utf8Decoder ?? util_utf8_1.fromUtf8,
        utf8Encoder: config?.utf8Encoder ?? util_utf8_1.toUtf8,
    };
};
exports.getRuntimeConfig = getRuntimeConfig;
