import { Command as $Command } from "@smithy/smithy-client";
import { MetadataBearer as __MetadataBearer } from "@smithy/types";
import {
  GetBucketMetadataConfigurationOutput,
  GetBucketMetadataConfigurationRequest,
} from "../models/models_0";
import {
  S3ClientResolvedConfig,
  ServiceInputTypes,
  ServiceOutputTypes,
} from "../S3Client";
export { __MetadataBearer };
export { $Command };
export interface GetBucketMetadataConfigurationCommandInput
  extends GetBucketMetadataConfigurationRequest {}
export interface GetBucketMetadataConfigurationCommandOutput
  extends GetBucketMetadataConfigurationOutput,
    __MetadataBearer {}
declare const GetBucketMetadataConfigurationCommand_base: {
  new (
    input: GetBucketMetadataConfigurationCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    GetBucketMetadataConfigurationCommandInput,
    GetBucketMetadataConfigurationCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  new (
    input: GetBucketMetadataConfigurationCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    GetBucketMetadataConfigurationCommandInput,
    GetBucketMetadataConfigurationCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  getEndpointParameterInstructions(): import("@smithy/middleware-endpoint").EndpointParameterInstructions;
};
export declare class GetBucketMetadataConfigurationCommand extends GetBucketMetadataConfigurationCommand_base {
  protected static __types: {
    api: {
      input: GetBucketMetadataConfigurationRequest;
      output: GetBucketMetadataConfigurationOutput;
    };
    sdk: {
      input: GetBucketMetadataConfigurationCommandInput;
      output: GetBucketMetadataConfigurationCommandOutput;
    };
  };
}
