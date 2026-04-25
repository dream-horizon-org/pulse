import { ExceptionOptionType as __ExceptionOptionType } from "@smithy/smithy-client";
import { IntelligentTieringAccessTier, StorageClass } from "./enums";
import { S3ServiceException as __BaseException } from "./S3ServiceException";
export declare class NoSuchUpload extends __BaseException {
  readonly name: "NoSuchUpload";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<NoSuchUpload, __BaseException>);
}
export declare class AccessDenied extends __BaseException {
  readonly name: "AccessDenied";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<AccessDenied, __BaseException>);
}
export declare class ObjectNotInActiveTierError extends __BaseException {
  readonly name: "ObjectNotInActiveTierError";
  readonly $fault: "client";
  constructor(
    opts: __ExceptionOptionType<ObjectNotInActiveTierError, __BaseException>
  );
}
export declare class BucketAlreadyExists extends __BaseException {
  readonly name: "BucketAlreadyExists";
  readonly $fault: "client";
  constructor(
    opts: __ExceptionOptionType<BucketAlreadyExists, __BaseException>
  );
}
export declare class BucketAlreadyOwnedByYou extends __BaseException {
  readonly name: "BucketAlreadyOwnedByYou";
  readonly $fault: "client";
  constructor(
    opts: __ExceptionOptionType<BucketAlreadyOwnedByYou, __BaseException>
  );
}
export declare class NoSuchBucket extends __BaseException {
  readonly name: "NoSuchBucket";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<NoSuchBucket, __BaseException>);
}
export declare class InvalidObjectState extends __BaseException {
  readonly name: "InvalidObjectState";
  readonly $fault: "client";
  StorageClass?: StorageClass | undefined;
  AccessTier?: IntelligentTieringAccessTier | undefined;
  constructor(opts: __ExceptionOptionType<InvalidObjectState, __BaseException>);
}
export declare class NoSuchKey extends __BaseException {
  readonly name: "NoSuchKey";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<NoSuchKey, __BaseException>);
}
export declare class NotFound extends __BaseException {
  readonly name: "NotFound";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<NotFound, __BaseException>);
}
export declare class EncryptionTypeMismatch extends __BaseException {
  readonly name: "EncryptionTypeMismatch";
  readonly $fault: "client";
  constructor(
    opts: __ExceptionOptionType<EncryptionTypeMismatch, __BaseException>
  );
}
export declare class InvalidRequest extends __BaseException {
  readonly name: "InvalidRequest";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<InvalidRequest, __BaseException>);
}
export declare class InvalidWriteOffset extends __BaseException {
  readonly name: "InvalidWriteOffset";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<InvalidWriteOffset, __BaseException>);
}
export declare class TooManyParts extends __BaseException {
  readonly name: "TooManyParts";
  readonly $fault: "client";
  constructor(opts: __ExceptionOptionType<TooManyParts, __BaseException>);
}
export declare class IdempotencyParameterMismatch extends __BaseException {
  readonly name: "IdempotencyParameterMismatch";
  readonly $fault: "client";
  constructor(
    opts: __ExceptionOptionType<IdempotencyParameterMismatch, __BaseException>
  );
}
export declare class ObjectAlreadyInActiveTierError extends __BaseException {
  readonly name: "ObjectAlreadyInActiveTierError";
  readonly $fault: "client";
  constructor(
    opts: __ExceptionOptionType<ObjectAlreadyInActiveTierError, __BaseException>
  );
}
