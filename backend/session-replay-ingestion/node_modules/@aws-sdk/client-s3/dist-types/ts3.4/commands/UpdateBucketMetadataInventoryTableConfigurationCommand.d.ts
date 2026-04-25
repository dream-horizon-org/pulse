import { Command as $Command } from "@smithy/smithy-client";
import { MetadataBearer as __MetadataBearer } from "@smithy/types";
import { UpdateBucketMetadataInventoryTableConfigurationRequest } from "../models/models_1";
import {
  S3ClientResolvedConfig,
  ServiceInputTypes,
  ServiceOutputTypes,
} from "../S3Client";
export { __MetadataBearer };
export { $Command };
export interface UpdateBucketMetadataInventoryTableConfigurationCommandInput
  extends UpdateBucketMetadataInventoryTableConfigurationRequest {}
export interface UpdateBucketMetadataInventoryTableConfigurationCommandOutput
  extends __MetadataBearer {}
declare const UpdateBucketMetadataInventoryTableConfigurationCommand_base: {
  new (
    input: UpdateBucketMetadataInventoryTableConfigurationCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    UpdateBucketMetadataInventoryTableConfigurationCommandInput,
    UpdateBucketMetadataInventoryTableConfigurationCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  new (
    input: UpdateBucketMetadataInventoryTableConfigurationCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    UpdateBucketMetadataInventoryTableConfigurationCommandInput,
    UpdateBucketMetadataInventoryTableConfigurationCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  getEndpointParameterInstructions(): import("@smithy/middleware-endpoint").EndpointParameterInstructions;
};
export declare class UpdateBucketMetadataInventoryTableConfigurationCommand extends UpdateBucketMetadataInventoryTableConfigurationCommand_base {
  protected static __types: {
    api: {
      input: UpdateBucketMetadataInventoryTableConfigurationRequest;
      output: {};
    };
    sdk: {
      input: UpdateBucketMetadataInventoryTableConfigurationCommandInput;
      output: UpdateBucketMetadataInventoryTableConfigurationCommandOutput;
    };
  };
}
