import { type WaiterConfiguration, type WaiterResult } from "@smithy/util-waiter";
import { type HeadObjectCommandInput } from "../commands/HeadObjectCommand";
import type { S3Client } from "../S3Client";
/**
 *
 *  @deprecated Use waitUntilObjectNotExists instead. waitForObjectNotExists does not throw error in non-success cases.
 */
export declare const waitForObjectNotExists: (params: WaiterConfiguration<S3Client>, input: HeadObjectCommandInput) => Promise<WaiterResult>;
/**
 *
 *  @param params - Waiter configuration options.
 *  @param input - The input to HeadObjectCommand for polling.
 */
export declare const waitUntilObjectNotExists: (params: WaiterConfiguration<S3Client>, input: HeadObjectCommandInput) => Promise<WaiterResult>;
