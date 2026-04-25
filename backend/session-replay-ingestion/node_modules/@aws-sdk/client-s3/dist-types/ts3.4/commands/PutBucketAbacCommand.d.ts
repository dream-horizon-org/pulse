import { Command as $Command } from "@smithy/smithy-client";
import { MetadataBearer as __MetadataBearer } from "@smithy/types";
import { PutBucketAbacRequest } from "../models/models_0";
import {
  S3ClientResolvedConfig,
  ServiceInputTypes,
  ServiceOutputTypes,
} from "../S3Client";
export { __MetadataBearer };
export { $Command };
export interface PutBucketAbacCommandInput extends PutBucketAbacRequest {}
export interface PutBucketAbacCommandOutput extends __MetadataBearer {}
declare const PutBucketAbacCommand_base: {
  new (
    input: PutBucketAbacCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    PutBucketAbacCommandInput,
    PutBucketAbacCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  new (
    input: PutBucketAbacCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    PutBucketAbacCommandInput,
    PutBucketAbacCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  getEndpointParameterInstructions(): import("@smithy/middleware-endpoint").EndpointParameterInstructions;
};
export declare class PutBucketAbacCommand extends PutBucketAbacCommand_base {
  protected static __types: {
    api: {
      input: PutBucketAbacRequest;
      output: {};
    };
    sdk: {
      input: PutBucketAbacCommandInput;
      output: PutBucketAbacCommandOutput;
    };
  };
}
