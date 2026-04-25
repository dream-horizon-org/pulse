import {
  CompleteMultipartUploadCommandInput,
  CreateMultipartUploadCommandInput,
  PutObjectCommandInput,
  S3Client,
  Tag,
  UploadPartCommandInput,
} from "@aws-sdk/client-s3";
import { AbortController as AbortControllerPolyfill } from "@smithy/types";
export interface Progress {
  loaded?: number;
  total?: number;
  part?: number;
  Key?: string;
  Bucket?: string;
}
export type BodyDataTypes = PutObjectCommandInput["Body"];
export type ServiceClients = S3Client;
export interface Configuration {
  queueSize: number;
  partSize: number;
  leavePartsOnError: boolean;
  tags: Tag[];
  abortController?: AbortController | AbortControllerPolyfill;
}
export interface Options extends Partial<Configuration> {
  params: PutObjectCommandInput &
    Partial<
      CreateMultipartUploadCommandInput &
        UploadPartCommandInput &
        CompleteMultipartUploadCommandInput
    >;
  client: S3Client;
}
