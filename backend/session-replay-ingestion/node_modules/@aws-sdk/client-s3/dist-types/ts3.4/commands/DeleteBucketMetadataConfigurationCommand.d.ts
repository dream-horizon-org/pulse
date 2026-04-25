import { Command as $Command } from "@smithy/smithy-client";
import { MetadataBearer as __MetadataBearer } from "@smithy/types";
import { DeleteBucketMetadataConfigurationRequest } from "../models/models_0";
import {
  S3ClientResolvedConfig,
  ServiceInputTypes,
  ServiceOutputTypes,
} from "../S3Client";
export { __MetadataBearer };
export { $Command };
export interface DeleteBucketMetadataConfigurationCommandInput
  extends DeleteBucketMetadataConfigurationRequest {}
export interface DeleteBucketMetadataConfigurationCommandOutput
  extends __MetadataBearer {}
declare const DeleteBucketMetadataConfigurationCommand_base: {
  new (
    input: DeleteBucketMetadataConfigurationCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    DeleteBucketMetadataConfigurationCommandInput,
    DeleteBucketMetadataConfigurationCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  new (
    input: DeleteBucketMetadataConfigurationCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    DeleteBucketMetadataConfigurationCommandInput,
    DeleteBucketMetadataConfigurationCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  getEndpointParameterInstructions(): import("@smithy/middleware-endpoint").EndpointParameterInstructions;
};
export declare class DeleteBucketMetadataConfigurationCommand extends DeleteBucketMetadataConfigurationCommand_base {
  protected static __types: {
    api: {
      input: DeleteBucketMetadataConfigurationRequest;
      output: {};
    };
    sdk: {
      input: DeleteBucketMetadataConfigurationCommandInput;
      output: DeleteBucketMetadataConfigurationCommandOutput;
    };
  };
}
