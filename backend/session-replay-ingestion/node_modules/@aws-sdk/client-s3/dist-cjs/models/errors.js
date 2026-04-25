"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ObjectAlreadyInActiveTierError = exports.IdempotencyParameterMismatch = exports.TooManyParts = exports.InvalidWriteOffset = exports.InvalidRequest = exports.EncryptionTypeMismatch = exports.NotFound = exports.NoSuchKey = exports.InvalidObjectState = exports.NoSuchBucket = exports.BucketAlreadyOwnedByYou = exports.BucketAlreadyExists = exports.ObjectNotInActiveTierError = exports.AccessDenied = exports.NoSuchUpload = void 0;
const S3ServiceException_1 = require("./S3ServiceException");
class NoSuchUpload extends S3ServiceException_1.S3ServiceException {
    name = "NoSuchUpload";
    $fault = "client";
    constructor(opts) {
        super({
            name: "NoSuchUpload",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, NoSuchUpload.prototype);
    }
}
exports.NoSuchUpload = NoSuchUpload;
class AccessDenied extends S3ServiceException_1.S3ServiceException {
    name = "AccessDenied";
    $fault = "client";
    constructor(opts) {
        super({
            name: "AccessDenied",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, AccessDenied.prototype);
    }
}
exports.AccessDenied = AccessDenied;
class ObjectNotInActiveTierError extends S3ServiceException_1.S3ServiceException {
    name = "ObjectNotInActiveTierError";
    $fault = "client";
    constructor(opts) {
        super({
            name: "ObjectNotInActiveTierError",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, ObjectNotInActiveTierError.prototype);
    }
}
exports.ObjectNotInActiveTierError = ObjectNotInActiveTierError;
class BucketAlreadyExists extends S3ServiceException_1.S3ServiceException {
    name = "BucketAlreadyExists";
    $fault = "client";
    constructor(opts) {
        super({
            name: "BucketAlreadyExists",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, BucketAlreadyExists.prototype);
    }
}
exports.BucketAlreadyExists = BucketAlreadyExists;
class BucketAlreadyOwnedByYou extends S3ServiceException_1.S3ServiceException {
    name = "BucketAlreadyOwnedByYou";
    $fault = "client";
    constructor(opts) {
        super({
            name: "BucketAlreadyOwnedByYou",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, BucketAlreadyOwnedByYou.prototype);
    }
}
exports.BucketAlreadyOwnedByYou = BucketAlreadyOwnedByYou;
class NoSuchBucket extends S3ServiceException_1.S3ServiceException {
    name = "NoSuchBucket";
    $fault = "client";
    constructor(opts) {
        super({
            name: "NoSuchBucket",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, NoSuchBucket.prototype);
    }
}
exports.NoSuchBucket = NoSuchBucket;
class InvalidObjectState extends S3ServiceException_1.S3ServiceException {
    name = "InvalidObjectState";
    $fault = "client";
    StorageClass;
    AccessTier;
    constructor(opts) {
        super({
            name: "InvalidObjectState",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, InvalidObjectState.prototype);
        this.StorageClass = opts.StorageClass;
        this.AccessTier = opts.AccessTier;
    }
}
exports.InvalidObjectState = InvalidObjectState;
class NoSuchKey extends S3ServiceException_1.S3ServiceException {
    name = "NoSuchKey";
    $fault = "client";
    constructor(opts) {
        super({
            name: "NoSuchKey",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, NoSuchKey.prototype);
    }
}
exports.NoSuchKey = NoSuchKey;
class NotFound extends S3ServiceException_1.S3ServiceException {
    name = "NotFound";
    $fault = "client";
    constructor(opts) {
        super({
            name: "NotFound",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, NotFound.prototype);
    }
}
exports.NotFound = NotFound;
class EncryptionTypeMismatch extends S3ServiceException_1.S3ServiceException {
    name = "EncryptionTypeMismatch";
    $fault = "client";
    constructor(opts) {
        super({
            name: "EncryptionTypeMismatch",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, EncryptionTypeMismatch.prototype);
    }
}
exports.EncryptionTypeMismatch = EncryptionTypeMismatch;
class InvalidRequest extends S3ServiceException_1.S3ServiceException {
    name = "InvalidRequest";
    $fault = "client";
    constructor(opts) {
        super({
            name: "InvalidRequest",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, InvalidRequest.prototype);
    }
}
exports.InvalidRequest = InvalidRequest;
class InvalidWriteOffset extends S3ServiceException_1.S3ServiceException {
    name = "InvalidWriteOffset";
    $fault = "client";
    constructor(opts) {
        super({
            name: "InvalidWriteOffset",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, InvalidWriteOffset.prototype);
    }
}
exports.InvalidWriteOffset = InvalidWriteOffset;
class TooManyParts extends S3ServiceException_1.S3ServiceException {
    name = "TooManyParts";
    $fault = "client";
    constructor(opts) {
        super({
            name: "TooManyParts",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, TooManyParts.prototype);
    }
}
exports.TooManyParts = TooManyParts;
class IdempotencyParameterMismatch extends S3ServiceException_1.S3ServiceException {
    name = "IdempotencyParameterMismatch";
    $fault = "client";
    constructor(opts) {
        super({
            name: "IdempotencyParameterMismatch",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, IdempotencyParameterMismatch.prototype);
    }
}
exports.IdempotencyParameterMismatch = IdempotencyParameterMismatch;
class ObjectAlreadyInActiveTierError extends S3ServiceException_1.S3ServiceException {
    name = "ObjectAlreadyInActiveTierError";
    $fault = "client";
    constructor(opts) {
        super({
            name: "ObjectAlreadyInActiveTierError",
            $fault: "client",
            ...opts,
        });
        Object.setPrototypeOf(this, ObjectAlreadyInActiveTierError.prototype);
    }
}
exports.ObjectAlreadyInActiveTierError = ObjectAlreadyInActiveTierError;
