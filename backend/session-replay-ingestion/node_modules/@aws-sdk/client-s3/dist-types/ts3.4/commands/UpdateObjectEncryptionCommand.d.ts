import { Command as $Command } from "@smithy/smithy-client";
import { MetadataBearer as __MetadataBearer } from "@smithy/types";
import {
  UpdateObjectEncryptionRequest,
  UpdateObjectEncryptionResponse,
} from "../models/models_1";
import {
  S3ClientResolvedConfig,
  ServiceInputTypes,
  ServiceOutputTypes,
} from "../S3Client";
export { __MetadataBearer };
export { $Command };
export interface UpdateObjectEncryptionCommandInput
  extends UpdateObjectEncryptionRequest {}
export interface UpdateObjectEncryptionCommandOutput
  extends UpdateObjectEncryptionResponse,
    __MetadataBearer {}
declare const UpdateObjectEncryptionCommand_base: {
  new (
    input: UpdateObjectEncryptionCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    UpdateObjectEncryptionCommandInput,
    UpdateObjectEncryptionCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  new (
    input: UpdateObjectEncryptionCommandInput
  ): import("@smithy/smithy-client").CommandImpl<
    UpdateObjectEncryptionCommandInput,
    UpdateObjectEncryptionCommandOutput,
    S3ClientResolvedConfig,
    ServiceInputTypes,
    ServiceOutputTypes
  >;
  getEndpointParameterInstructions(): import("@smithy/middleware-endpoint").EndpointParameterInstructions;
};
export declare class UpdateObjectEncryptionCommand extends UpdateObjectEncryptionCommand_base {
  protected static __types: {
    api: {
      input: UpdateObjectEncryptionRequest;
      output: UpdateObjectEncryptionResponse;
    };
    sdk: {
      input: UpdateObjectEncryptionCommandInput;
      output: UpdateObjectEncryptionCommandOutput;
    };
  };
}
