"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CreateBucketMetadataTableConfigurationRequest$ = exports.CreateBucketMetadataConfigurationRequest$ = exports.CreateBucketConfiguration$ = exports.CORSRule$ = exports.CORSConfiguration$ = exports.CopyPartResult$ = exports.CopyObjectResult$ = exports.CopyObjectRequest$ = exports.CopyObjectOutput$ = exports.ContinuationEvent$ = exports.Condition$ = exports.CompleteMultipartUploadRequest$ = exports.CompleteMultipartUploadOutput$ = exports.CompletedPart$ = exports.CompletedMultipartUpload$ = exports.CommonPrefix$ = exports.Checksum$ = exports.BucketLoggingStatus$ = exports.BucketLifecycleConfiguration$ = exports.BucketInfo$ = exports.Bucket$ = exports.BlockedEncryptionTypes$ = exports.AnalyticsS3BucketDestination$ = exports.AnalyticsExportDestination$ = exports.AnalyticsConfiguration$ = exports.AnalyticsAndOperator$ = exports.AccessControlTranslation$ = exports.AccessControlPolicy$ = exports.AccelerateConfiguration$ = exports.AbortMultipartUploadRequest$ = exports.AbortMultipartUploadOutput$ = exports.AbortIncompleteMultipartUpload$ = exports.AbacStatus$ = exports.errorTypeRegistries = exports.TooManyParts$ = exports.ObjectNotInActiveTierError$ = exports.ObjectAlreadyInActiveTierError$ = exports.NotFound$ = exports.NoSuchUpload$ = exports.NoSuchKey$ = exports.NoSuchBucket$ = exports.InvalidWriteOffset$ = exports.InvalidRequest$ = exports.InvalidObjectState$ = exports.IdempotencyParameterMismatch$ = exports.EncryptionTypeMismatch$ = exports.BucketAlreadyOwnedByYou$ = exports.BucketAlreadyExists$ = exports.AccessDenied$ = exports.S3ServiceException$ = void 0;
exports.GetBucketAccelerateConfigurationRequest$ = exports.GetBucketAccelerateConfigurationOutput$ = exports.GetBucketAbacRequest$ = exports.GetBucketAbacOutput$ = exports.FilterRule$ = exports.ExistingObjectReplication$ = exports.EventBridgeConfiguration$ = exports.ErrorDocument$ = exports.ErrorDetails$ = exports._Error$ = exports.EndEvent$ = exports.EncryptionConfiguration$ = exports.Encryption$ = exports.DestinationResult$ = exports.Destination$ = exports.DeletePublicAccessBlockRequest$ = exports.DeleteObjectTaggingRequest$ = exports.DeleteObjectTaggingOutput$ = exports.DeleteObjectsRequest$ = exports.DeleteObjectsOutput$ = exports.DeleteObjectRequest$ = exports.DeleteObjectOutput$ = exports.DeleteMarkerReplication$ = exports.DeleteMarkerEntry$ = exports.DeletedObject$ = exports.DeleteBucketWebsiteRequest$ = exports.DeleteBucketTaggingRequest$ = exports.DeleteBucketRequest$ = exports.DeleteBucketReplicationRequest$ = exports.DeleteBucketPolicyRequest$ = exports.DeleteBucketOwnershipControlsRequest$ = exports.DeleteBucketMetricsConfigurationRequest$ = exports.DeleteBucketMetadataTableConfigurationRequest$ = exports.DeleteBucketMetadataConfigurationRequest$ = exports.DeleteBucketLifecycleRequest$ = exports.DeleteBucketInventoryConfigurationRequest$ = exports.DeleteBucketIntelligentTieringConfigurationRequest$ = exports.DeleteBucketEncryptionRequest$ = exports.DeleteBucketCorsRequest$ = exports.DeleteBucketAnalyticsConfigurationRequest$ = exports.Delete$ = exports.DefaultRetention$ = exports.CSVOutput$ = exports.CSVInput$ = exports.CreateSessionRequest$ = exports.CreateSessionOutput$ = exports.CreateMultipartUploadRequest$ = exports.CreateMultipartUploadOutput$ = exports.CreateBucketRequest$ = exports.CreateBucketOutput$ = void 0;
exports.GetObjectLegalHoldRequest$ = exports.GetObjectLegalHoldOutput$ = exports.GetObjectAttributesRequest$ = exports.GetObjectAttributesParts$ = exports.GetObjectAttributesOutput$ = exports.GetObjectAclRequest$ = exports.GetObjectAclOutput$ = exports.GetBucketWebsiteRequest$ = exports.GetBucketWebsiteOutput$ = exports.GetBucketVersioningRequest$ = exports.GetBucketVersioningOutput$ = exports.GetBucketTaggingRequest$ = exports.GetBucketTaggingOutput$ = exports.GetBucketRequestPaymentRequest$ = exports.GetBucketRequestPaymentOutput$ = exports.GetBucketReplicationRequest$ = exports.GetBucketReplicationOutput$ = exports.GetBucketPolicyStatusRequest$ = exports.GetBucketPolicyStatusOutput$ = exports.GetBucketPolicyRequest$ = exports.GetBucketPolicyOutput$ = exports.GetBucketOwnershipControlsRequest$ = exports.GetBucketOwnershipControlsOutput$ = exports.GetBucketNotificationConfigurationRequest$ = exports.GetBucketMetricsConfigurationRequest$ = exports.GetBucketMetricsConfigurationOutput$ = exports.GetBucketMetadataTableConfigurationResult$ = exports.GetBucketMetadataTableConfigurationRequest$ = exports.GetBucketMetadataTableConfigurationOutput$ = exports.GetBucketMetadataConfigurationResult$ = exports.GetBucketMetadataConfigurationRequest$ = exports.GetBucketMetadataConfigurationOutput$ = exports.GetBucketLoggingRequest$ = exports.GetBucketLoggingOutput$ = exports.GetBucketLocationRequest$ = exports.GetBucketLocationOutput$ = exports.GetBucketLifecycleConfigurationRequest$ = exports.GetBucketLifecycleConfigurationOutput$ = exports.GetBucketInventoryConfigurationRequest$ = exports.GetBucketInventoryConfigurationOutput$ = exports.GetBucketIntelligentTieringConfigurationRequest$ = exports.GetBucketIntelligentTieringConfigurationOutput$ = exports.GetBucketEncryptionRequest$ = exports.GetBucketEncryptionOutput$ = exports.GetBucketCorsRequest$ = exports.GetBucketCorsOutput$ = exports.GetBucketAnalyticsConfigurationRequest$ = exports.GetBucketAnalyticsConfigurationOutput$ = exports.GetBucketAclRequest$ = exports.GetBucketAclOutput$ = void 0;
exports.ListBucketInventoryConfigurationsRequest$ = exports.ListBucketInventoryConfigurationsOutput$ = exports.ListBucketIntelligentTieringConfigurationsRequest$ = exports.ListBucketIntelligentTieringConfigurationsOutput$ = exports.ListBucketAnalyticsConfigurationsRequest$ = exports.ListBucketAnalyticsConfigurationsOutput$ = exports.LifecycleRuleFilter$ = exports.LifecycleRuleAndOperator$ = exports.LifecycleRule$ = exports.LifecycleExpiration$ = exports.LambdaFunctionConfiguration$ = exports.JSONOutput$ = exports.JSONInput$ = exports.JournalTableConfigurationUpdates$ = exports.JournalTableConfigurationResult$ = exports.JournalTableConfiguration$ = exports.InventoryTableConfigurationUpdates$ = exports.InventoryTableConfigurationResult$ = exports.InventoryTableConfiguration$ = exports.InventorySchedule$ = exports.InventoryS3BucketDestination$ = exports.InventoryFilter$ = exports.InventoryEncryption$ = exports.InventoryDestination$ = exports.InventoryConfiguration$ = exports.IntelligentTieringFilter$ = exports.IntelligentTieringConfiguration$ = exports.IntelligentTieringAndOperator$ = exports.InputSerialization$ = exports.Initiator$ = exports.IndexDocument$ = exports.HeadObjectRequest$ = exports.HeadObjectOutput$ = exports.HeadBucketRequest$ = exports.HeadBucketOutput$ = exports.Grantee$ = exports.Grant$ = exports.GlacierJobParameters$ = exports.GetPublicAccessBlockRequest$ = exports.GetPublicAccessBlockOutput$ = exports.GetObjectTorrentRequest$ = exports.GetObjectTorrentOutput$ = exports.GetObjectTaggingRequest$ = exports.GetObjectTaggingOutput$ = exports.GetObjectRetentionRequest$ = exports.GetObjectRetentionOutput$ = exports.GetObjectRequest$ = exports.GetObjectOutput$ = exports.GetObjectLockConfigurationRequest$ = exports.GetObjectLockConfigurationOutput$ = void 0;
exports.Progress$ = exports.PolicyStatus$ = exports.PartitionedPrefix$ = exports.Part$ = exports.ParquetInput$ = exports.OwnershipControlsRule$ = exports.OwnershipControls$ = exports.Owner$ = exports.OutputSerialization$ = exports.OutputLocation$ = exports.ObjectVersion$ = exports.ObjectPart$ = exports.ObjectLockRule$ = exports.ObjectLockRetention$ = exports.ObjectLockLegalHold$ = exports.ObjectLockConfiguration$ = exports.ObjectIdentifier$ = exports._Object$ = exports.NotificationConfigurationFilter$ = exports.NotificationConfiguration$ = exports.NoncurrentVersionTransition$ = exports.NoncurrentVersionExpiration$ = exports.MultipartUpload$ = exports.MetricsConfiguration$ = exports.MetricsAndOperator$ = exports.Metrics$ = exports.MetadataTableEncryptionConfiguration$ = exports.MetadataTableConfigurationResult$ = exports.MetadataTableConfiguration$ = exports.MetadataEntry$ = exports.MetadataConfigurationResult$ = exports.MetadataConfiguration$ = exports.LoggingEnabled$ = exports.LocationInfo$ = exports.ListPartsRequest$ = exports.ListPartsOutput$ = exports.ListObjectVersionsRequest$ = exports.ListObjectVersionsOutput$ = exports.ListObjectsV2Request$ = exports.ListObjectsV2Output$ = exports.ListObjectsRequest$ = exports.ListObjectsOutput$ = exports.ListMultipartUploadsRequest$ = exports.ListMultipartUploadsOutput$ = exports.ListDirectoryBucketsRequest$ = exports.ListDirectoryBucketsOutput$ = exports.ListBucketsRequest$ = exports.ListBucketsOutput$ = exports.ListBucketMetricsConfigurationsRequest$ = exports.ListBucketMetricsConfigurationsOutput$ = void 0;
exports.RequestPaymentConfiguration$ = exports.ReplicationTimeValue$ = exports.ReplicationTime$ = exports.ReplicationRuleFilter$ = exports.ReplicationRuleAndOperator$ = exports.ReplicationRule$ = exports.ReplicationConfiguration$ = exports.ReplicaModifications$ = exports.RenameObjectRequest$ = exports.RenameObjectOutput$ = exports.RedirectAllRequestsTo$ = exports.Redirect$ = exports.RecordsEvent$ = exports.RecordExpiration$ = exports.QueueConfiguration$ = exports.PutPublicAccessBlockRequest$ = exports.PutObjectTaggingRequest$ = exports.PutObjectTaggingOutput$ = exports.PutObjectRetentionRequest$ = exports.PutObjectRetentionOutput$ = exports.PutObjectRequest$ = exports.PutObjectOutput$ = exports.PutObjectLockConfigurationRequest$ = exports.PutObjectLockConfigurationOutput$ = exports.PutObjectLegalHoldRequest$ = exports.PutObjectLegalHoldOutput$ = exports.PutObjectAclRequest$ = exports.PutObjectAclOutput$ = exports.PutBucketWebsiteRequest$ = exports.PutBucketVersioningRequest$ = exports.PutBucketTaggingRequest$ = exports.PutBucketRequestPaymentRequest$ = exports.PutBucketReplicationRequest$ = exports.PutBucketPolicyRequest$ = exports.PutBucketOwnershipControlsRequest$ = exports.PutBucketNotificationConfigurationRequest$ = exports.PutBucketMetricsConfigurationRequest$ = exports.PutBucketLoggingRequest$ = exports.PutBucketLifecycleConfigurationRequest$ = exports.PutBucketLifecycleConfigurationOutput$ = exports.PutBucketInventoryConfigurationRequest$ = exports.PutBucketIntelligentTieringConfigurationRequest$ = exports.PutBucketEncryptionRequest$ = exports.PutBucketCorsRequest$ = exports.PutBucketAnalyticsConfigurationRequest$ = exports.PutBucketAclRequest$ = exports.PutBucketAccelerateConfigurationRequest$ = exports.PutBucketAbacRequest$ = exports.PublicAccessBlockConfiguration$ = exports.ProgressEvent$ = void 0;
exports.SelectObjectContentEventStream$ = exports.ObjectEncryption$ = exports.MetricsFilter$ = exports.AnalyticsFilter$ = exports.WriteGetObjectResponseRequest$ = exports.WebsiteConfiguration$ = exports.VersioningConfiguration$ = exports.UploadPartRequest$ = exports.UploadPartOutput$ = exports.UploadPartCopyRequest$ = exports.UploadPartCopyOutput$ = exports.UpdateObjectEncryptionResponse$ = exports.UpdateObjectEncryptionRequest$ = exports.UpdateBucketMetadataJournalTableConfigurationRequest$ = exports.UpdateBucketMetadataInventoryTableConfigurationRequest$ = exports.Transition$ = exports.TopicConfiguration$ = exports.Tiering$ = exports.TargetObjectKeyFormat$ = exports.TargetGrant$ = exports.Tagging$ = exports.Tag$ = exports.StorageClassAnalysisDataExport$ = exports.StorageClassAnalysis$ = exports.StatsEvent$ = exports.Stats$ = exports.SSES3$ = exports.SSEKMSEncryption$ = exports.SseKmsEncryptedObjects$ = exports.SSEKMS$ = exports.SourceSelectionCriteria$ = exports.SimplePrefix$ = exports.SessionCredentials$ = exports.ServerSideEncryptionRule$ = exports.ServerSideEncryptionConfiguration$ = exports.ServerSideEncryptionByDefault$ = exports.SelectParameters$ = exports.SelectObjectContentRequest$ = exports.SelectObjectContentOutput$ = exports.ScanRange$ = exports.S3TablesDestinationResult$ = exports.S3TablesDestination$ = exports.S3Location$ = exports.S3KeyFilter$ = exports.RoutingRule$ = exports.RestoreStatus$ = exports.RestoreRequest$ = exports.RestoreObjectRequest$ = exports.RestoreObjectOutput$ = exports.RequestProgress$ = void 0;
exports.GetBucketWebsite$ = exports.GetBucketVersioning$ = exports.GetBucketTagging$ = exports.GetBucketRequestPayment$ = exports.GetBucketReplication$ = exports.GetBucketPolicyStatus$ = exports.GetBucketPolicy$ = exports.GetBucketOwnershipControls$ = exports.GetBucketNotificationConfiguration$ = exports.GetBucketMetricsConfiguration$ = exports.GetBucketMetadataTableConfiguration$ = exports.GetBucketMetadataConfiguration$ = exports.GetBucketLogging$ = exports.GetBucketLocation$ = exports.GetBucketLifecycleConfiguration$ = exports.GetBucketInventoryConfiguration$ = exports.GetBucketIntelligentTieringConfiguration$ = exports.GetBucketEncryption$ = exports.GetBucketCors$ = exports.GetBucketAnalyticsConfiguration$ = exports.GetBucketAcl$ = exports.GetBucketAccelerateConfiguration$ = exports.GetBucketAbac$ = exports.DeletePublicAccessBlock$ = exports.DeleteObjectTagging$ = exports.DeleteObjects$ = exports.DeleteObject$ = exports.DeleteBucketWebsite$ = exports.DeleteBucketTagging$ = exports.DeleteBucketReplication$ = exports.DeleteBucketPolicy$ = exports.DeleteBucketOwnershipControls$ = exports.DeleteBucketMetricsConfiguration$ = exports.DeleteBucketMetadataTableConfiguration$ = exports.DeleteBucketMetadataConfiguration$ = exports.DeleteBucketLifecycle$ = exports.DeleteBucketInventoryConfiguration$ = exports.DeleteBucketIntelligentTieringConfiguration$ = exports.DeleteBucketEncryption$ = exports.DeleteBucketCors$ = exports.DeleteBucketAnalyticsConfiguration$ = exports.DeleteBucket$ = exports.CreateSession$ = exports.CreateMultipartUpload$ = exports.CreateBucketMetadataTableConfiguration$ = exports.CreateBucketMetadataConfiguration$ = exports.CreateBucket$ = exports.CopyObject$ = exports.CompleteMultipartUpload$ = exports.AbortMultipartUpload$ = void 0;
exports.RestoreObject$ = exports.RenameObject$ = exports.PutPublicAccessBlock$ = exports.PutObjectTagging$ = exports.PutObjectRetention$ = exports.PutObjectLockConfiguration$ = exports.PutObjectLegalHold$ = exports.PutObjectAcl$ = exports.PutObject$ = exports.PutBucketWebsite$ = exports.PutBucketVersioning$ = exports.PutBucketTagging$ = exports.PutBucketRequestPayment$ = exports.PutBucketReplication$ = exports.PutBucketPolicy$ = exports.PutBucketOwnershipControls$ = exports.PutBucketNotificationConfiguration$ = exports.PutBucketMetricsConfiguration$ = exports.PutBucketLogging$ = exports.PutBucketLifecycleConfiguration$ = exports.PutBucketInventoryConfiguration$ = exports.PutBucketIntelligentTieringConfiguration$ = exports.PutBucketEncryption$ = exports.PutBucketCors$ = exports.PutBucketAnalyticsConfiguration$ = exports.PutBucketAcl$ = exports.PutBucketAccelerateConfiguration$ = exports.PutBucketAbac$ = exports.ListParts$ = exports.ListObjectVersions$ = exports.ListObjectsV2$ = exports.ListObjects$ = exports.ListMultipartUploads$ = exports.ListDirectoryBuckets$ = exports.ListBuckets$ = exports.ListBucketMetricsConfigurations$ = exports.ListBucketInventoryConfigurations$ = exports.ListBucketIntelligentTieringConfigurations$ = exports.ListBucketAnalyticsConfigurations$ = exports.HeadObject$ = exports.HeadBucket$ = exports.GetPublicAccessBlock$ = exports.GetObjectTorrent$ = exports.GetObjectTagging$ = exports.GetObjectRetention$ = exports.GetObjectLockConfiguration$ = exports.GetObjectLegalHold$ = exports.GetObjectAttributes$ = exports.GetObjectAcl$ = exports.GetObject$ = void 0;
exports.WriteGetObjectResponse$ = exports.UploadPartCopy$ = exports.UploadPart$ = exports.UpdateObjectEncryption$ = exports.UpdateBucketMetadataJournalTableConfiguration$ = exports.UpdateBucketMetadataInventoryTableConfiguration$ = exports.SelectObjectContent$ = void 0;
const _A = "Account";
const _AAO = "AnalyticsAndOperator";
const _AC = "AccelerateConfiguration";
const _ACL = "AccessControlList";
const _ACL_ = "ACL";
const _ACLn = "AnalyticsConfigurationList";
const _ACP = "AccessControlPolicy";
const _ACT = "AccessControlTranslation";
const _ACn = "AnalyticsConfiguration";
const _AD = "AccessDenied";
const _ADb = "AbortDate";
const _AED = "AnalyticsExportDestination";
const _AF = "AnalyticsFilter";
const _AH = "AllowedHeaders";
const _AHl = "AllowedHeader";
const _AI = "AccountId";
const _AIMU = "AbortIncompleteMultipartUpload";
const _AKI = "AccessKeyId";
const _AM = "AllowedMethods";
const _AMU = "AbortMultipartUpload";
const _AMUO = "AbortMultipartUploadOutput";
const _AMUR = "AbortMultipartUploadRequest";
const _AMl = "AllowedMethod";
const _AO = "AllowedOrigins";
const _AOl = "AllowedOrigin";
const _APA = "AccessPointAlias";
const _APAc = "AccessPointArn";
const _AQRD = "AllowQuotedRecordDelimiter";
const _AR = "AcceptRanges";
const _ARI = "AbortRuleId";
const _AS = "AbacStatus";
const _ASBD = "AnalyticsS3BucketDestination";
const _ASSEBD = "ApplyServerSideEncryptionByDefault";
const _ASr = "ArchiveStatus";
const _AT = "AccessTier";
const _An = "And";
const _B = "Bucket";
const _BA = "BucketArn";
const _BAE = "BucketAlreadyExists";
const _BAI = "BucketAccountId";
const _BAOBY = "BucketAlreadyOwnedByYou";
const _BET = "BlockedEncryptionTypes";
const _BGR = "BypassGovernanceRetention";
const _BI = "BucketInfo";
const _BKE = "BucketKeyEnabled";
const _BLC = "BucketLifecycleConfiguration";
const _BLN = "BucketLocationName";
const _BLS = "BucketLoggingStatus";
const _BLT = "BucketLocationType";
const _BN = "BucketNamespace";
const _BNu = "BucketName";
const _BP = "BytesProcessed";
const _BPA = "BlockPublicAcls";
const _BPP = "BlockPublicPolicy";
const _BR = "BucketRegion";
const _BRy = "BytesReturned";
const _BS = "BytesScanned";
const _Bo = "Body";
const _Bu = "Buckets";
const _C = "Checksum";
const _CA = "ChecksumAlgorithm";
const _CACL = "CannedACL";
const _CB = "CreateBucket";
const _CBC = "CreateBucketConfiguration";
const _CBMC = "CreateBucketMetadataConfiguration";
const _CBMCR = "CreateBucketMetadataConfigurationRequest";
const _CBMTC = "CreateBucketMetadataTableConfiguration";
const _CBMTCR = "CreateBucketMetadataTableConfigurationRequest";
const _CBO = "CreateBucketOutput";
const _CBR = "CreateBucketRequest";
const _CC = "CacheControl";
const _CCRC = "ChecksumCRC32";
const _CCRCC = "ChecksumCRC32C";
const _CCRCNVME = "ChecksumCRC64NVME";
const _CC_ = "Cache-Control";
const _CD = "CreationDate";
const _CD_ = "Content-Disposition";
const _CDo = "ContentDisposition";
const _CE = "ContinuationEvent";
const _CE_ = "Content-Encoding";
const _CEo = "ContentEncoding";
const _CF = "CloudFunction";
const _CFC = "CloudFunctionConfiguration";
const _CL = "ContentLanguage";
const _CL_ = "Content-Language";
const _CL__ = "Content-Length";
const _CLo = "ContentLength";
const _CM = "Content-MD5";
const _CMD = "ChecksumMD5";
const _CMDo = "ContentMD5";
const _CMU = "CompletedMultipartUpload";
const _CMUO = "CompleteMultipartUploadOutput";
const _CMUOr = "CreateMultipartUploadOutput";
const _CMUR = "CompleteMultipartUploadResult";
const _CMURo = "CompleteMultipartUploadRequest";
const _CMURr = "CreateMultipartUploadRequest";
const _CMUo = "CompleteMultipartUpload";
const _CMUr = "CreateMultipartUpload";
const _CMh = "ChecksumMode";
const _CO = "CopyObject";
const _COO = "CopyObjectOutput";
const _COR = "CopyObjectResult";
const _CORSC = "CORSConfiguration";
const _CORSR = "CORSRules";
const _CORSRu = "CORSRule";
const _CORo = "CopyObjectRequest";
const _CP = "CommonPrefix";
const _CPL = "CommonPrefixList";
const _CPLo = "CompletedPartList";
const _CPR = "CopyPartResult";
const _CPo = "CompletedPart";
const _CPom = "CommonPrefixes";
const _CR = "ContentRange";
const _CRSBA = "ConfirmRemoveSelfBucketAccess";
const _CR_ = "Content-Range";
const _CS = "CopySource";
const _CSHA = "ChecksumSHA1";
const _CSHAh = "ChecksumSHA256";
const _CSHAhe = "ChecksumSHA512";
const _CSIM = "CopySourceIfMatch";
const _CSIMS = "CopySourceIfModifiedSince";
const _CSINM = "CopySourceIfNoneMatch";
const _CSIUS = "CopySourceIfUnmodifiedSince";
const _CSO = "CreateSessionOutput";
const _CSR = "CreateSessionResult";
const _CSRo = "CopySourceRange";
const _CSRr = "CreateSessionRequest";
const _CSSSECA = "CopySourceSSECustomerAlgorithm";
const _CSSSECK = "CopySourceSSECustomerKey";
const _CSSSECKMD = "CopySourceSSECustomerKeyMD5";
const _CSV = "CSV";
const _CSVI = "CopySourceVersionId";
const _CSVIn = "CSVInput";
const _CSVO = "CSVOutput";
const _CSo = "ConfigurationState";
const _CSr = "CreateSession";
const _CT = "ChecksumType";
const _CT_ = "Content-Type";
const _CTl = "ClientToken";
const _CTo = "ContentType";
const _CTom = "CompressionType";
const _CTon = "ContinuationToken";
const _CXXHASH = "ChecksumXXHASH64";
const _CXXHASHh = "ChecksumXXHASH3";
const _CXXHASHhe = "ChecksumXXHASH128";
const _Co = "Condition";
const _Cod = "Code";
const _Com = "Comments";
const _Con = "Contents";
const _Cont = "Cont";
const _Cr = "Credentials";
const _D = "Days";
const _DAI = "DaysAfterInitiation";
const _DB = "DeleteBucket";
const _DBAC = "DeleteBucketAnalyticsConfiguration";
const _DBACR = "DeleteBucketAnalyticsConfigurationRequest";
const _DBC = "DeleteBucketCors";
const _DBCR = "DeleteBucketCorsRequest";
const _DBE = "DeleteBucketEncryption";
const _DBER = "DeleteBucketEncryptionRequest";
const _DBIC = "DeleteBucketInventoryConfiguration";
const _DBICR = "DeleteBucketInventoryConfigurationRequest";
const _DBITC = "DeleteBucketIntelligentTieringConfiguration";
const _DBITCR = "DeleteBucketIntelligentTieringConfigurationRequest";
const _DBL = "DeleteBucketLifecycle";
const _DBLR = "DeleteBucketLifecycleRequest";
const _DBMC = "DeleteBucketMetadataConfiguration";
const _DBMCR = "DeleteBucketMetadataConfigurationRequest";
const _DBMCRe = "DeleteBucketMetricsConfigurationRequest";
const _DBMCe = "DeleteBucketMetricsConfiguration";
const _DBMTC = "DeleteBucketMetadataTableConfiguration";
const _DBMTCR = "DeleteBucketMetadataTableConfigurationRequest";
const _DBOC = "DeleteBucketOwnershipControls";
const _DBOCR = "DeleteBucketOwnershipControlsRequest";
const _DBP = "DeleteBucketPolicy";
const _DBPR = "DeleteBucketPolicyRequest";
const _DBR = "DeleteBucketRequest";
const _DBRR = "DeleteBucketReplicationRequest";
const _DBRe = "DeleteBucketReplication";
const _DBT = "DeleteBucketTagging";
const _DBTR = "DeleteBucketTaggingRequest";
const _DBW = "DeleteBucketWebsite";
const _DBWR = "DeleteBucketWebsiteRequest";
const _DE = "DataExport";
const _DIM = "DestinationIfMatch";
const _DIMS = "DestinationIfModifiedSince";
const _DINM = "DestinationIfNoneMatch";
const _DIUS = "DestinationIfUnmodifiedSince";
const _DM = "DeleteMarker";
const _DME = "DeleteMarkerEntry";
const _DMR = "DeleteMarkerReplication";
const _DMVI = "DeleteMarkerVersionId";
const _DMe = "DeleteMarkers";
const _DN = "DisplayName";
const _DO = "DeletedObject";
const _DOO = "DeleteObjectOutput";
const _DOOe = "DeleteObjectsOutput";
const _DOR = "DeleteObjectRequest";
const _DORe = "DeleteObjectsRequest";
const _DOT = "DeleteObjectTagging";
const _DOTO = "DeleteObjectTaggingOutput";
const _DOTR = "DeleteObjectTaggingRequest";
const _DOe = "DeletedObjects";
const _DOel = "DeleteObject";
const _DOele = "DeleteObjects";
const _DPAB = "DeletePublicAccessBlock";
const _DPABR = "DeletePublicAccessBlockRequest";
const _DR = "DataRedundancy";
const _DRe = "DefaultRetention";
const _DRel = "DeleteResult";
const _DRes = "DestinationResult";
const _Da = "Date";
const _De = "Delete";
const _Del = "Deleted";
const _Deli = "Delimiter";
const _Des = "Destination";
const _Desc = "Description";
const _Det = "Details";
const _E = "Expiration";
const _EA = "EmailAddress";
const _EBC = "EventBridgeConfiguration";
const _EBO = "ExpectedBucketOwner";
const _EC = "EncryptionConfiguration";
const _ECr = "ErrorCode";
const _ED = "ErrorDetails";
const _EDr = "ErrorDocument";
const _EE = "EndEvent";
const _EH = "ExposeHeaders";
const _EHx = "ExposeHeader";
const _EM = "ErrorMessage";
const _EODM = "ExpiredObjectDeleteMarker";
const _EOR = "ExistingObjectReplication";
const _ES = "ExpiresString";
const _ESBO = "ExpectedSourceBucketOwner";
const _ET = "EncryptionType";
const _ETL = "EncryptionTypeList";
const _ETM = "EncryptionTypeMismatch";
const _ETa = "ETag";
const _ETn = "EncodingType";
const _ETv = "EventThreshold";
const _ETx = "ExpressionType";
const _En = "Encryption";
const _Ena = "Enabled";
const _End = "End";
const _Er = "Errors";
const _Err = "Error";
const _Ev = "Events";
const _Eve = "Event";
const _Ex = "Expires";
const _Exp = "Expression";
const _F = "Filter";
const _FD = "FieldDelimiter";
const _FHI = "FileHeaderInfo";
const _FO = "FetchOwner";
const _FR = "FilterRule";
const _FRL = "FilterRuleList";
const _FRi = "FilterRules";
const _Fi = "Field";
const _Fo = "Format";
const _Fr = "Frequency";
const _G = "Grants";
const _GBA = "GetBucketAbac";
const _GBAC = "GetBucketAccelerateConfiguration";
const _GBACO = "GetBucketAccelerateConfigurationOutput";
const _GBACOe = "GetBucketAnalyticsConfigurationOutput";
const _GBACR = "GetBucketAccelerateConfigurationRequest";
const _GBACRe = "GetBucketAnalyticsConfigurationRequest";
const _GBACe = "GetBucketAnalyticsConfiguration";
const _GBAO = "GetBucketAbacOutput";
const _GBAOe = "GetBucketAclOutput";
const _GBAR = "GetBucketAbacRequest";
const _GBARe = "GetBucketAclRequest";
const _GBAe = "GetBucketAcl";
const _GBC = "GetBucketCors";
const _GBCO = "GetBucketCorsOutput";
const _GBCR = "GetBucketCorsRequest";
const _GBE = "GetBucketEncryption";
const _GBEO = "GetBucketEncryptionOutput";
const _GBER = "GetBucketEncryptionRequest";
const _GBIC = "GetBucketInventoryConfiguration";
const _GBICO = "GetBucketInventoryConfigurationOutput";
const _GBICR = "GetBucketInventoryConfigurationRequest";
const _GBITC = "GetBucketIntelligentTieringConfiguration";
const _GBITCO = "GetBucketIntelligentTieringConfigurationOutput";
const _GBITCR = "GetBucketIntelligentTieringConfigurationRequest";
const _GBL = "GetBucketLocation";
const _GBLC = "GetBucketLifecycleConfiguration";
const _GBLCO = "GetBucketLifecycleConfigurationOutput";
const _GBLCR = "GetBucketLifecycleConfigurationRequest";
const _GBLO = "GetBucketLocationOutput";
const _GBLOe = "GetBucketLoggingOutput";
const _GBLR = "GetBucketLocationRequest";
const _GBLRe = "GetBucketLoggingRequest";
const _GBLe = "GetBucketLogging";
const _GBMC = "GetBucketMetadataConfiguration";
const _GBMCO = "GetBucketMetadataConfigurationOutput";
const _GBMCOe = "GetBucketMetricsConfigurationOutput";
const _GBMCR = "GetBucketMetadataConfigurationResult";
const _GBMCRe = "GetBucketMetadataConfigurationRequest";
const _GBMCRet = "GetBucketMetricsConfigurationRequest";
const _GBMCe = "GetBucketMetricsConfiguration";
const _GBMTC = "GetBucketMetadataTableConfiguration";
const _GBMTCO = "GetBucketMetadataTableConfigurationOutput";
const _GBMTCR = "GetBucketMetadataTableConfigurationResult";
const _GBMTCRe = "GetBucketMetadataTableConfigurationRequest";
const _GBNC = "GetBucketNotificationConfiguration";
const _GBNCR = "GetBucketNotificationConfigurationRequest";
const _GBOC = "GetBucketOwnershipControls";
const _GBOCO = "GetBucketOwnershipControlsOutput";
const _GBOCR = "GetBucketOwnershipControlsRequest";
const _GBP = "GetBucketPolicy";
const _GBPO = "GetBucketPolicyOutput";
const _GBPR = "GetBucketPolicyRequest";
const _GBPS = "GetBucketPolicyStatus";
const _GBPSO = "GetBucketPolicyStatusOutput";
const _GBPSR = "GetBucketPolicyStatusRequest";
const _GBR = "GetBucketReplication";
const _GBRO = "GetBucketReplicationOutput";
const _GBRP = "GetBucketRequestPayment";
const _GBRPO = "GetBucketRequestPaymentOutput";
const _GBRPR = "GetBucketRequestPaymentRequest";
const _GBRR = "GetBucketReplicationRequest";
const _GBT = "GetBucketTagging";
const _GBTO = "GetBucketTaggingOutput";
const _GBTR = "GetBucketTaggingRequest";
const _GBV = "GetBucketVersioning";
const _GBVO = "GetBucketVersioningOutput";
const _GBVR = "GetBucketVersioningRequest";
const _GBW = "GetBucketWebsite";
const _GBWO = "GetBucketWebsiteOutput";
const _GBWR = "GetBucketWebsiteRequest";
const _GFC = "GrantFullControl";
const _GJP = "GlacierJobParameters";
const _GO = "GetObject";
const _GOA = "GetObjectAcl";
const _GOAO = "GetObjectAclOutput";
const _GOAOe = "GetObjectAttributesOutput";
const _GOAP = "GetObjectAttributesParts";
const _GOAR = "GetObjectAclRequest";
const _GOARe = "GetObjectAttributesResponse";
const _GOARet = "GetObjectAttributesRequest";
const _GOAe = "GetObjectAttributes";
const _GOLC = "GetObjectLockConfiguration";
const _GOLCO = "GetObjectLockConfigurationOutput";
const _GOLCR = "GetObjectLockConfigurationRequest";
const _GOLH = "GetObjectLegalHold";
const _GOLHO = "GetObjectLegalHoldOutput";
const _GOLHR = "GetObjectLegalHoldRequest";
const _GOO = "GetObjectOutput";
const _GOR = "GetObjectRequest";
const _GORO = "GetObjectRetentionOutput";
const _GORR = "GetObjectRetentionRequest";
const _GORe = "GetObjectRetention";
const _GOT = "GetObjectTagging";
const _GOTO = "GetObjectTaggingOutput";
const _GOTOe = "GetObjectTorrentOutput";
const _GOTR = "GetObjectTaggingRequest";
const _GOTRe = "GetObjectTorrentRequest";
const _GOTe = "GetObjectTorrent";
const _GPAB = "GetPublicAccessBlock";
const _GPABO = "GetPublicAccessBlockOutput";
const _GPABR = "GetPublicAccessBlockRequest";
const _GR = "GrantRead";
const _GRACP = "GrantReadACP";
const _GW = "GrantWrite";
const _GWACP = "GrantWriteACP";
const _Gr = "Grant";
const _Gra = "Grantee";
const _HB = "HeadBucket";
const _HBO = "HeadBucketOutput";
const _HBR = "HeadBucketRequest";
const _HECRE = "HttpErrorCodeReturnedEquals";
const _HN = "HostName";
const _HO = "HeadObject";
const _HOO = "HeadObjectOutput";
const _HOR = "HeadObjectRequest";
const _HRC = "HttpRedirectCode";
const _I = "Id";
const _IC = "InventoryConfiguration";
const _ICL = "InventoryConfigurationList";
const _ID = "ID";
const _IDn = "IndexDocument";
const _IDnv = "InventoryDestination";
const _IE = "IsEnabled";
const _IEn = "InventoryEncryption";
const _IF = "InventoryFilter";
const _IL = "IsLatest";
const _IM = "IfMatch";
const _IMIT = "IfMatchInitiatedTime";
const _IMLMT = "IfMatchLastModifiedTime";
const _IMS = "IfMatchSize";
const _IMS_ = "If-Modified-Since";
const _IMSf = "IfModifiedSince";
const _IMUR = "InitiateMultipartUploadResult";
const _IM_ = "If-Match";
const _INM = "IfNoneMatch";
const _INM_ = "If-None-Match";
const _IOF = "InventoryOptionalFields";
const _IOS = "InvalidObjectState";
const _IOV = "IncludedObjectVersions";
const _IP = "IsPublic";
const _IPA = "IgnorePublicAcls";
const _IPM = "IdempotencyParameterMismatch";
const _IR = "InvalidRequest";
const _IRIP = "IsRestoreInProgress";
const _IS = "InputSerialization";
const _ISBD = "InventoryS3BucketDestination";
const _ISn = "InventorySchedule";
const _IT = "IsTruncated";
const _ITAO = "IntelligentTieringAndOperator";
const _ITC = "IntelligentTieringConfiguration";
const _ITCL = "IntelligentTieringConfigurationList";
const _ITCR = "InventoryTableConfigurationResult";
const _ITCU = "InventoryTableConfigurationUpdates";
const _ITCn = "InventoryTableConfiguration";
const _ITF = "IntelligentTieringFilter";
const _IUS = "IfUnmodifiedSince";
const _IUS_ = "If-Unmodified-Since";
const _IWO = "InvalidWriteOffset";
const _In = "Initiator";
const _Ini = "Initiated";
const _JSON = "JSON";
const _JSONI = "JSONInput";
const _JSONO = "JSONOutput";
const _JTC = "JournalTableConfiguration";
const _JTCR = "JournalTableConfigurationResult";
const _JTCU = "JournalTableConfigurationUpdates";
const _K = "Key";
const _KC = "KeyCount";
const _KI = "KeyId";
const _KKA = "KmsKeyArn";
const _KM = "KeyMarker";
const _KMSC = "KMSContext";
const _KMSKA = "KMSKeyArn";
const _KMSKI = "KMSKeyId";
const _KMSMKID = "KMSMasterKeyID";
const _KPE = "KeyPrefixEquals";
const _L = "Location";
const _LAMBR = "ListAllMyBucketsResult";
const _LAMDBR = "ListAllMyDirectoryBucketsResult";
const _LB = "ListBuckets";
const _LBAC = "ListBucketAnalyticsConfigurations";
const _LBACO = "ListBucketAnalyticsConfigurationsOutput";
const _LBACR = "ListBucketAnalyticsConfigurationResult";
const _LBACRi = "ListBucketAnalyticsConfigurationsRequest";
const _LBIC = "ListBucketInventoryConfigurations";
const _LBICO = "ListBucketInventoryConfigurationsOutput";
const _LBICR = "ListBucketInventoryConfigurationsRequest";
const _LBITC = "ListBucketIntelligentTieringConfigurations";
const _LBITCO = "ListBucketIntelligentTieringConfigurationsOutput";
const _LBITCR = "ListBucketIntelligentTieringConfigurationsRequest";
const _LBMC = "ListBucketMetricsConfigurations";
const _LBMCO = "ListBucketMetricsConfigurationsOutput";
const _LBMCR = "ListBucketMetricsConfigurationsRequest";
const _LBO = "ListBucketsOutput";
const _LBR = "ListBucketsRequest";
const _LBRi = "ListBucketResult";
const _LC = "LocationConstraint";
const _LCi = "LifecycleConfiguration";
const _LDB = "ListDirectoryBuckets";
const _LDBO = "ListDirectoryBucketsOutput";
const _LDBR = "ListDirectoryBucketsRequest";
const _LE = "LoggingEnabled";
const _LEi = "LifecycleExpiration";
const _LFA = "LambdaFunctionArn";
const _LFC = "LambdaFunctionConfiguration";
const _LFCL = "LambdaFunctionConfigurationList";
const _LFCa = "LambdaFunctionConfigurations";
const _LH = "LegalHold";
const _LI = "LocationInfo";
const _LICR = "ListInventoryConfigurationsResult";
const _LM = "LastModified";
const _LMCR = "ListMetricsConfigurationsResult";
const _LMT = "LastModifiedTime";
const _LMU = "ListMultipartUploads";
const _LMUO = "ListMultipartUploadsOutput";
const _LMUR = "ListMultipartUploadsResult";
const _LMURi = "ListMultipartUploadsRequest";
const _LM_ = "Last-Modified";
const _LO = "ListObjects";
const _LOO = "ListObjectsOutput";
const _LOR = "ListObjectsRequest";
const _LOV = "ListObjectsV2";
const _LOVO = "ListObjectsV2Output";
const _LOVOi = "ListObjectVersionsOutput";
const _LOVR = "ListObjectsV2Request";
const _LOVRi = "ListObjectVersionsRequest";
const _LOVi = "ListObjectVersions";
const _LP = "ListParts";
const _LPO = "ListPartsOutput";
const _LPR = "ListPartsResult";
const _LPRi = "ListPartsRequest";
const _LR = "LifecycleRule";
const _LRAO = "LifecycleRuleAndOperator";
const _LRF = "LifecycleRuleFilter";
const _LRi = "LifecycleRules";
const _LVR = "ListVersionsResult";
const _M = "Metadata";
const _MAO = "MetricsAndOperator";
const _MAS = "MaxAgeSeconds";
const _MB = "MaxBuckets";
const _MC = "MetadataConfiguration";
const _MCL = "MetricsConfigurationList";
const _MCR = "MetadataConfigurationResult";
const _MCe = "MetricsConfiguration";
const _MD = "MetadataDirective";
const _MDB = "MaxDirectoryBuckets";
const _MDf = "MfaDelete";
const _ME = "MetadataEntry";
const _MF = "MetricsFilter";
const _MFA = "MFA";
const _MFAD = "MFADelete";
const _MK = "MaxKeys";
const _MM = "MissingMeta";
const _MOS = "MpuObjectSize";
const _MP = "MaxParts";
const _MTC = "MetadataTableConfiguration";
const _MTCR = "MetadataTableConfigurationResult";
const _MTEC = "MetadataTableEncryptionConfiguration";
const _MU = "MultipartUpload";
const _MUL = "MultipartUploadList";
const _MUa = "MaxUploads";
const _Ma = "Marker";
const _Me = "Metrics";
const _Mes = "Message";
const _Mi = "Minutes";
const _Mo = "Mode";
const _N = "Name";
const _NC = "NotificationConfiguration";
const _NCF = "NotificationConfigurationFilter";
const _NCT = "NextContinuationToken";
const _ND = "NoncurrentDays";
const _NEKKAS = "NonEmptyKmsKeyArnString";
const _NF = "NotFound";
const _NKM = "NextKeyMarker";
const _NM = "NextMarker";
const _NNV = "NewerNoncurrentVersions";
const _NPNM = "NextPartNumberMarker";
const _NSB = "NoSuchBucket";
const _NSK = "NoSuchKey";
const _NSU = "NoSuchUpload";
const _NUIM = "NextUploadIdMarker";
const _NVE = "NoncurrentVersionExpiration";
const _NVIM = "NextVersionIdMarker";
const _NVT = "NoncurrentVersionTransitions";
const _NVTL = "NoncurrentVersionTransitionList";
const _NVTo = "NoncurrentVersionTransition";
const _O = "Owner";
const _OA = "ObjectAttributes";
const _OAIATE = "ObjectAlreadyInActiveTierError";
const _OC = "OwnershipControls";
const _OCR = "OwnershipControlsRule";
const _OCRw = "OwnershipControlsRules";
const _OE = "ObjectEncryption";
const _OF = "OptionalFields";
const _OI = "ObjectIdentifier";
const _OIL = "ObjectIdentifierList";
const _OL = "OutputLocation";
const _OLC = "ObjectLockConfiguration";
const _OLE = "ObjectLockEnabled";
const _OLEFB = "ObjectLockEnabledForBucket";
const _OLLH = "ObjectLockLegalHold";
const _OLLHS = "ObjectLockLegalHoldStatus";
const _OLM = "ObjectLockMode";
const _OLR = "ObjectLockRetention";
const _OLRUD = "ObjectLockRetainUntilDate";
const _OLRb = "ObjectLockRule";
const _OLb = "ObjectList";
const _ONIATE = "ObjectNotInActiveTierError";
const _OO = "ObjectOwnership";
const _OOA = "OptionalObjectAttributes";
const _OP = "ObjectParts";
const _OPb = "ObjectPart";
const _OS = "ObjectSize";
const _OSGT = "ObjectSizeGreaterThan";
const _OSLT = "ObjectSizeLessThan";
const _OSV = "OutputSchemaVersion";
const _OSu = "OutputSerialization";
const _OV = "ObjectVersion";
const _OVL = "ObjectVersionList";
const _Ob = "Objects";
const _Obj = "Object";
const _P = "Prefix";
const _PABC = "PublicAccessBlockConfiguration";
const _PBA = "PutBucketAbac";
const _PBAC = "PutBucketAccelerateConfiguration";
const _PBACR = "PutBucketAccelerateConfigurationRequest";
const _PBACRu = "PutBucketAnalyticsConfigurationRequest";
const _PBACu = "PutBucketAnalyticsConfiguration";
const _PBAR = "PutBucketAbacRequest";
const _PBARu = "PutBucketAclRequest";
const _PBAu = "PutBucketAcl";
const _PBC = "PutBucketCors";
const _PBCR = "PutBucketCorsRequest";
const _PBE = "PutBucketEncryption";
const _PBER = "PutBucketEncryptionRequest";
const _PBIC = "PutBucketInventoryConfiguration";
const _PBICR = "PutBucketInventoryConfigurationRequest";
const _PBITC = "PutBucketIntelligentTieringConfiguration";
const _PBITCR = "PutBucketIntelligentTieringConfigurationRequest";
const _PBL = "PutBucketLogging";
const _PBLC = "PutBucketLifecycleConfiguration";
const _PBLCO = "PutBucketLifecycleConfigurationOutput";
const _PBLCR = "PutBucketLifecycleConfigurationRequest";
const _PBLR = "PutBucketLoggingRequest";
const _PBMC = "PutBucketMetricsConfiguration";
const _PBMCR = "PutBucketMetricsConfigurationRequest";
const _PBNC = "PutBucketNotificationConfiguration";
const _PBNCR = "PutBucketNotificationConfigurationRequest";
const _PBOC = "PutBucketOwnershipControls";
const _PBOCR = "PutBucketOwnershipControlsRequest";
const _PBP = "PutBucketPolicy";
const _PBPR = "PutBucketPolicyRequest";
const _PBR = "PutBucketReplication";
const _PBRP = "PutBucketRequestPayment";
const _PBRPR = "PutBucketRequestPaymentRequest";
const _PBRR = "PutBucketReplicationRequest";
const _PBT = "PutBucketTagging";
const _PBTR = "PutBucketTaggingRequest";
const _PBV = "PutBucketVersioning";
const _PBVR = "PutBucketVersioningRequest";
const _PBW = "PutBucketWebsite";
const _PBWR = "PutBucketWebsiteRequest";
const _PC = "PartsCount";
const _PDS = "PartitionDateSource";
const _PE = "ProgressEvent";
const _PI = "ParquetInput";
const _PL = "PartsList";
const _PN = "PartNumber";
const _PNM = "PartNumberMarker";
const _PO = "PutObject";
const _POA = "PutObjectAcl";
const _POAO = "PutObjectAclOutput";
const _POAR = "PutObjectAclRequest";
const _POLC = "PutObjectLockConfiguration";
const _POLCO = "PutObjectLockConfigurationOutput";
const _POLCR = "PutObjectLockConfigurationRequest";
const _POLH = "PutObjectLegalHold";
const _POLHO = "PutObjectLegalHoldOutput";
const _POLHR = "PutObjectLegalHoldRequest";
const _POO = "PutObjectOutput";
const _POR = "PutObjectRequest";
const _PORO = "PutObjectRetentionOutput";
const _PORR = "PutObjectRetentionRequest";
const _PORu = "PutObjectRetention";
const _POT = "PutObjectTagging";
const _POTO = "PutObjectTaggingOutput";
const _POTR = "PutObjectTaggingRequest";
const _PP = "PartitionedPrefix";
const _PPAB = "PutPublicAccessBlock";
const _PPABR = "PutPublicAccessBlockRequest";
const _PS = "PolicyStatus";
const _Pa = "Parts";
const _Par = "Part";
const _Parq = "Parquet";
const _Pay = "Payer";
const _Payl = "Payload";
const _Pe = "Permission";
const _Po = "Policy";
const _Pr = "Progress";
const _Pri = "Priority";
const _Pro = "Protocol";
const _Q = "Quiet";
const _QA = "QueueArn";
const _QC = "QuoteCharacter";
const _QCL = "QueueConfigurationList";
const _QCu = "QueueConfigurations";
const _QCue = "QueueConfiguration";
const _QEC = "QuoteEscapeCharacter";
const _QF = "QuoteFields";
const _Qu = "Queue";
const _R = "Rules";
const _RART = "RedirectAllRequestsTo";
const _RC = "RequestCharged";
const _RCC = "ResponseCacheControl";
const _RCD = "ResponseContentDisposition";
const _RCE = "ResponseContentEncoding";
const _RCL = "ResponseContentLanguage";
const _RCT = "ResponseContentType";
const _RCe = "ReplicationConfiguration";
const _RD = "RecordDelimiter";
const _RE = "ResponseExpires";
const _RED = "RestoreExpiryDate";
const _REe = "RecordExpiration";
const _REec = "RecordsEvent";
const _RKKID = "ReplicaKmsKeyID";
const _RKPW = "ReplaceKeyPrefixWith";
const _RKW = "ReplaceKeyWith";
const _RM = "ReplicaModifications";
const _RO = "RenameObject";
const _ROO = "RenameObjectOutput";
const _ROOe = "RestoreObjectOutput";
const _ROP = "RestoreOutputPath";
const _ROR = "RenameObjectRequest";
const _RORe = "RestoreObjectRequest";
const _ROe = "RestoreObject";
const _RP = "RequestPayer";
const _RPB = "RestrictPublicBuckets";
const _RPC = "RequestPaymentConfiguration";
const _RPe = "RequestProgress";
const _RR = "RoutingRules";
const _RRAO = "ReplicationRuleAndOperator";
const _RRF = "ReplicationRuleFilter";
const _RRe = "ReplicationRule";
const _RRep = "ReplicationRules";
const _RReq = "RequestRoute";
const _RRes = "RestoreRequest";
const _RRo = "RoutingRule";
const _RS = "ReplicationStatus";
const _RSe = "RestoreStatus";
const _RSen = "RenameSource";
const _RT = "ReplicationTime";
const _RTV = "ReplicationTimeValue";
const _RTe = "RequestToken";
const _RUD = "RetainUntilDate";
const _Ra = "Range";
const _Re = "Restore";
const _Rec = "Records";
const _Red = "Redirect";
const _Ret = "Retention";
const _Ro = "Role";
const _Ru = "Rule";
const _S = "Status";
const _SA = "StartAfter";
const _SAK = "SecretAccessKey";
const _SAs = "SseAlgorithm";
const _SB = "StreamingBlob";
const _SBD = "S3BucketDestination";
const _SC = "StorageClass";
const _SCA = "StorageClassAnalysis";
const _SCADE = "StorageClassAnalysisDataExport";
const _SCV = "SessionCredentialValue";
const _SCe = "SessionCredentials";
const _SCt = "StatusCode";
const _SDV = "SkipDestinationValidation";
const _SE = "StatsEvent";
const _SIM = "SourceIfMatch";
const _SIMS = "SourceIfModifiedSince";
const _SINM = "SourceIfNoneMatch";
const _SIUS = "SourceIfUnmodifiedSince";
const _SK = "SSE-KMS";
const _SKEO = "SseKmsEncryptedObjects";
const _SKF = "S3KeyFilter";
const _SKe = "S3Key";
const _SL = "S3Location";
const _SM = "SessionMode";
const _SOC = "SelectObjectContent";
const _SOCES = "SelectObjectContentEventStream";
const _SOCO = "SelectObjectContentOutput";
const _SOCR = "SelectObjectContentRequest";
const _SP = "SelectParameters";
const _SPi = "SimplePrefix";
const _SR = "ScanRange";
const _SS = "SSE-S3";
const _SSC = "SourceSelectionCriteria";
const _SSE = "ServerSideEncryption";
const _SSEA = "SSEAlgorithm";
const _SSEBD = "ServerSideEncryptionByDefault";
const _SSEC = "ServerSideEncryptionConfiguration";
const _SSECA = "SSECustomerAlgorithm";
const _SSECK = "SSECustomerKey";
const _SSECKMD = "SSECustomerKeyMD5";
const _SSEKMS = "SSEKMS";
const _SSEKMSE = "SSEKMSEncryption";
const _SSEKMSEC = "SSEKMSEncryptionContext";
const _SSEKMSKI = "SSEKMSKeyId";
const _SSER = "ServerSideEncryptionRule";
const _SSERe = "ServerSideEncryptionRules";
const _SSES = "SSES3";
const _ST = "SessionToken";
const _STD = "S3TablesDestination";
const _STDR = "S3TablesDestinationResult";
const _S_ = "S3";
const _Sc = "Schedule";
const _Si = "Size";
const _St = "Start";
const _Sta = "Stats";
const _Su = "Suffix";
const _T = "Tags";
const _TA = "TableArn";
const _TAo = "TopicArn";
const _TB = "TargetBucket";
const _TBA = "TableBucketArn";
const _TBT = "TableBucketType";
const _TC = "TagCount";
const _TCL = "TopicConfigurationList";
const _TCo = "TopicConfigurations";
const _TCop = "TopicConfiguration";
const _TD = "TaggingDirective";
const _TDMOS = "TransitionDefaultMinimumObjectSize";
const _TG = "TargetGrants";
const _TGa = "TargetGrant";
const _TL = "TieringList";
const _TLr = "TransitionList";
const _TMP = "TooManyParts";
const _TN = "TableNamespace";
const _TNa = "TableName";
const _TOKF = "TargetObjectKeyFormat";
const _TP = "TargetPrefix";
const _TPC = "TotalPartsCount";
const _TS = "TagSet";
const _TSa = "TableStatus";
const _Ta = "Tag";
const _Tag = "Tagging";
const _Ti = "Tier";
const _Tie = "Tierings";
const _Tier = "Tiering";
const _Tim = "Time";
const _To = "Token";
const _Top = "Topic";
const _Tr = "Transitions";
const _Tra = "Transition";
const _Ty = "Type";
const _U = "Uploads";
const _UBMITC = "UpdateBucketMetadataInventoryTableConfiguration";
const _UBMITCR = "UpdateBucketMetadataInventoryTableConfigurationRequest";
const _UBMJTC = "UpdateBucketMetadataJournalTableConfiguration";
const _UBMJTCR = "UpdateBucketMetadataJournalTableConfigurationRequest";
const _UI = "UploadId";
const _UIM = "UploadIdMarker";
const _UM = "UserMetadata";
const _UOE = "UpdateObjectEncryption";
const _UOER = "UpdateObjectEncryptionRequest";
const _UOERp = "UpdateObjectEncryptionResponse";
const _UP = "UploadPart";
const _UPC = "UploadPartCopy";
const _UPCO = "UploadPartCopyOutput";
const _UPCR = "UploadPartCopyRequest";
const _UPO = "UploadPartOutput";
const _UPR = "UploadPartRequest";
const _URI = "URI";
const _Up = "Upload";
const _V = "Value";
const _VC = "VersioningConfiguration";
const _VI = "VersionId";
const _VIM = "VersionIdMarker";
const _Ve = "Versions";
const _Ver = "Version";
const _WC = "WebsiteConfiguration";
const _WGOR = "WriteGetObjectResponse";
const _WGORR = "WriteGetObjectResponseRequest";
const _WOB = "WriteOffsetBytes";
const _WRL = "WebsiteRedirectLocation";
const _Y = "Years";
const _ar = "accept-ranges";
const _br = "bucket-region";
const _c = "client";
const _ct = "continuation-token";
const _d = "delimiter";
const _e = "error";
const _eP = "eventPayload";
const _en = "endpoint";
const _et = "encoding-type";
const _fo = "fetch-owner";
const _h = "http";
const _hC = "httpChecksum";
const _hE = "httpError";
const _hH = "httpHeader";
const _hL = "hostLabel";
const _hP = "httpPayload";
const _hPH = "httpPrefixHeaders";
const _hQ = "httpQuery";
const _hi = "http://www.w3.org/2001/XMLSchema-instance";
const _i = "id";
const _iT = "idempotencyToken";
const _km = "key-marker";
const _m = "marker";
const _mb = "max-buckets";
const _mdb = "max-directory-buckets";
const _mk = "max-keys";
const _mp = "max-parts";
const _mu = "max-uploads";
const _p = "prefix";
const _pN = "partNumber";
const _pnm = "part-number-marker";
const _rcc = "response-cache-control";
const _rcd = "response-content-disposition";
const _rce = "response-content-encoding";
const _rcl = "response-content-language";
const _rct = "response-content-type";
const _re = "response-expires";
const _s = "smithy.ts.sdk.synthetic.com.amazonaws.s3";
const _sa = "start-after";
const _st = "streaming";
const _uI = "uploadId";
const _uim = "upload-id-marker";
const _vI = "versionId";
const _vim = "version-id-marker";
const _x = "xsi";
const _xA = "xmlAttribute";
const _xF = "xmlFlattened";
const _xN = "xmlName";
const _xNm = "xmlNamespace";
const _xaa = "x-amz-acl";
const _xaad = "x-amz-abort-date";
const _xaapa = "x-amz-access-point-alias";
const _xaari = "x-amz-abort-rule-id";
const _xaas = "x-amz-archive-status";
const _xaba = "x-amz-bucket-arn";
const _xabgr = "x-amz-bypass-governance-retention";
const _xabln = "x-amz-bucket-location-name";
const _xablt = "x-amz-bucket-location-type";
const _xabn = "x-amz-bucket-namespace";
const _xabole = "x-amz-bucket-object-lock-enabled";
const _xabolt = "x-amz-bucket-object-lock-token";
const _xabr = "x-amz-bucket-region";
const _xaca = "x-amz-checksum-algorithm";
const _xacc = "x-amz-checksum-crc32";
const _xacc_ = "x-amz-checksum-crc32c";
const _xacc__ = "x-amz-checksum-crc64nvme";
const _xacm = "x-amz-checksum-md5";
const _xacm_ = "x-amz-checksum-mode";
const _xacrsba = "x-amz-confirm-remove-self-bucket-access";
const _xacs = "x-amz-checksum-sha1";
const _xacs_ = "x-amz-checksum-sha256";
const _xacs__ = "x-amz-checksum-sha512";
const _xacs___ = "x-amz-copy-source";
const _xacsim = "x-amz-copy-source-if-match";
const _xacsims = "x-amz-copy-source-if-modified-since";
const _xacsinm = "x-amz-copy-source-if-none-match";
const _xacsius = "x-amz-copy-source-if-unmodified-since";
const _xacsm = "x-amz-create-session-mode";
const _xacsr = "x-amz-copy-source-range";
const _xacssseca = "x-amz-copy-source-server-side-encryption-customer-algorithm";
const _xacssseck = "x-amz-copy-source-server-side-encryption-customer-key";
const _xacssseckM = "x-amz-copy-source-server-side-encryption-customer-key-MD5";
const _xacsvi = "x-amz-copy-source-version-id";
const _xact = "x-amz-checksum-type";
const _xact_ = "x-amz-client-token";
const _xacx = "x-amz-checksum-xxhash64";
const _xacx_ = "x-amz-checksum-xxhash3";
const _xacx__ = "x-amz-checksum-xxhash128";
const _xadm = "x-amz-delete-marker";
const _xae = "x-amz-expiration";
const _xaebo = "x-amz-expected-bucket-owner";
const _xafec = "x-amz-fwd-error-code";
const _xafem = "x-amz-fwd-error-message";
const _xafhCC = "x-amz-fwd-header-Cache-Control";
const _xafhCD = "x-amz-fwd-header-Content-Disposition";
const _xafhCE = "x-amz-fwd-header-Content-Encoding";
const _xafhCL = "x-amz-fwd-header-Content-Language";
const _xafhCR = "x-amz-fwd-header-Content-Range";
const _xafhCT = "x-amz-fwd-header-Content-Type";
const _xafhE = "x-amz-fwd-header-ETag";
const _xafhE_ = "x-amz-fwd-header-Expires";
const _xafhLM = "x-amz-fwd-header-Last-Modified";
const _xafhar = "x-amz-fwd-header-accept-ranges";
const _xafhxacc = "x-amz-fwd-header-x-amz-checksum-crc32";
const _xafhxacc_ = "x-amz-fwd-header-x-amz-checksum-crc32c";
const _xafhxacc__ = "x-amz-fwd-header-x-amz-checksum-crc64nvme";
const _xafhxacm = "x-amz-fwd-header-x-amz-checksum-md5";
const _xafhxacs = "x-amz-fwd-header-x-amz-checksum-sha1";
const _xafhxacs_ = "x-amz-fwd-header-x-amz-checksum-sha256";
const _xafhxacs__ = "x-amz-fwd-header-x-amz-checksum-sha512";
const _xafhxacx = "x-amz-fwd-header-x-amz-checksum-xxhash64";
const _xafhxacx_ = "x-amz-fwd-header-x-amz-checksum-xxhash3";
const _xafhxacx__ = "x-amz-fwd-header-x-amz-checksum-xxhash128";
const _xafhxadm = "x-amz-fwd-header-x-amz-delete-marker";
const _xafhxae = "x-amz-fwd-header-x-amz-expiration";
const _xafhxamm = "x-amz-fwd-header-x-amz-missing-meta";
const _xafhxampc = "x-amz-fwd-header-x-amz-mp-parts-count";
const _xafhxaollh = "x-amz-fwd-header-x-amz-object-lock-legal-hold";
const _xafhxaolm = "x-amz-fwd-header-x-amz-object-lock-mode";
const _xafhxaolrud = "x-amz-fwd-header-x-amz-object-lock-retain-until-date";
const _xafhxar = "x-amz-fwd-header-x-amz-restore";
const _xafhxarc = "x-amz-fwd-header-x-amz-request-charged";
const _xafhxars = "x-amz-fwd-header-x-amz-replication-status";
const _xafhxasc = "x-amz-fwd-header-x-amz-storage-class";
const _xafhxasse = "x-amz-fwd-header-x-amz-server-side-encryption";
const _xafhxasseakki = "x-amz-fwd-header-x-amz-server-side-encryption-aws-kms-key-id";
const _xafhxassebke = "x-amz-fwd-header-x-amz-server-side-encryption-bucket-key-enabled";
const _xafhxasseca = "x-amz-fwd-header-x-amz-server-side-encryption-customer-algorithm";
const _xafhxasseckM = "x-amz-fwd-header-x-amz-server-side-encryption-customer-key-MD5";
const _xafhxatc = "x-amz-fwd-header-x-amz-tagging-count";
const _xafhxavi = "x-amz-fwd-header-x-amz-version-id";
const _xafs = "x-amz-fwd-status";
const _xagfc = "x-amz-grant-full-control";
const _xagr = "x-amz-grant-read";
const _xagra = "x-amz-grant-read-acp";
const _xagw = "x-amz-grant-write";
const _xagwa = "x-amz-grant-write-acp";
const _xaimit = "x-amz-if-match-initiated-time";
const _xaimlmt = "x-amz-if-match-last-modified-time";
const _xaims = "x-amz-if-match-size";
const _xam = "x-amz-meta-";
const _xam_ = "x-amz-mfa";
const _xamd = "x-amz-metadata-directive";
const _xamm = "x-amz-missing-meta";
const _xamos = "x-amz-mp-object-size";
const _xamp = "x-amz-max-parts";
const _xampc = "x-amz-mp-parts-count";
const _xaoa = "x-amz-object-attributes";
const _xaollh = "x-amz-object-lock-legal-hold";
const _xaolm = "x-amz-object-lock-mode";
const _xaolrud = "x-amz-object-lock-retain-until-date";
const _xaoo = "x-amz-object-ownership";
const _xaooa = "x-amz-optional-object-attributes";
const _xaos = "x-amz-object-size";
const _xapnm = "x-amz-part-number-marker";
const _xar = "x-amz-restore";
const _xarc = "x-amz-request-charged";
const _xarop = "x-amz-restore-output-path";
const _xarp = "x-amz-request-payer";
const _xarr = "x-amz-request-route";
const _xars = "x-amz-replication-status";
const _xars_ = "x-amz-rename-source";
const _xarsim = "x-amz-rename-source-if-match";
const _xarsims = "x-amz-rename-source-if-modified-since";
const _xarsinm = "x-amz-rename-source-if-none-match";
const _xarsius = "x-amz-rename-source-if-unmodified-since";
const _xart = "x-amz-request-token";
const _xasc = "x-amz-storage-class";
const _xasca = "x-amz-sdk-checksum-algorithm";
const _xasdv = "x-amz-skip-destination-validation";
const _xasebo = "x-amz-source-expected-bucket-owner";
const _xasse = "x-amz-server-side-encryption";
const _xasseakki = "x-amz-server-side-encryption-aws-kms-key-id";
const _xassebke = "x-amz-server-side-encryption-bucket-key-enabled";
const _xassec = "x-amz-server-side-encryption-context";
const _xasseca = "x-amz-server-side-encryption-customer-algorithm";
const _xasseck = "x-amz-server-side-encryption-customer-key";
const _xasseckM = "x-amz-server-side-encryption-customer-key-MD5";
const _xat = "x-amz-tagging";
const _xatc = "x-amz-tagging-count";
const _xatd = "x-amz-tagging-directive";
const _xatdmos = "x-amz-transition-default-minimum-object-size";
const _xavi = "x-amz-version-id";
const _xawob = "x-amz-write-offset-bytes";
const _xawrl = "x-amz-website-redirect-location";
const _xs = "xsi:type";
const n0 = "com.amazonaws.s3";
const schema_1 = require("@smithy/core/schema");
const errors_1 = require("../models/errors");
const S3ServiceException_1 = require("../models/S3ServiceException");
const _s_registry = schema_1.TypeRegistry.for(_s);
exports.S3ServiceException$ = [-3, _s, "S3ServiceException", 0, [], []];
_s_registry.registerError(exports.S3ServiceException$, S3ServiceException_1.S3ServiceException);
const n0_registry = schema_1.TypeRegistry.for(n0);
exports.AccessDenied$ = [-3, n0, _AD,
    { [_e]: _c, [_hE]: 403 },
    [],
    []
];
n0_registry.registerError(exports.AccessDenied$, errors_1.AccessDenied);
exports.BucketAlreadyExists$ = [-3, n0, _BAE,
    { [_e]: _c, [_hE]: 409 },
    [],
    []
];
n0_registry.registerError(exports.BucketAlreadyExists$, errors_1.BucketAlreadyExists);
exports.BucketAlreadyOwnedByYou$ = [-3, n0, _BAOBY,
    { [_e]: _c, [_hE]: 409 },
    [],
    []
];
n0_registry.registerError(exports.BucketAlreadyOwnedByYou$, errors_1.BucketAlreadyOwnedByYou);
exports.EncryptionTypeMismatch$ = [-3, n0, _ETM,
    { [_e]: _c, [_hE]: 400 },
    [],
    []
];
n0_registry.registerError(exports.EncryptionTypeMismatch$, errors_1.EncryptionTypeMismatch);
exports.IdempotencyParameterMismatch$ = [-3, n0, _IPM,
    { [_e]: _c, [_hE]: 400 },
    [],
    []
];
n0_registry.registerError(exports.IdempotencyParameterMismatch$, errors_1.IdempotencyParameterMismatch);
exports.InvalidObjectState$ = [-3, n0, _IOS,
    { [_e]: _c, [_hE]: 403 },
    [_SC, _AT],
    [0, 0]
];
n0_registry.registerError(exports.InvalidObjectState$, errors_1.InvalidObjectState);
exports.InvalidRequest$ = [-3, n0, _IR,
    { [_e]: _c, [_hE]: 400 },
    [],
    []
];
n0_registry.registerError(exports.InvalidRequest$, errors_1.InvalidRequest);
exports.InvalidWriteOffset$ = [-3, n0, _IWO,
    { [_e]: _c, [_hE]: 400 },
    [],
    []
];
n0_registry.registerError(exports.InvalidWriteOffset$, errors_1.InvalidWriteOffset);
exports.NoSuchBucket$ = [-3, n0, _NSB,
    { [_e]: _c, [_hE]: 404 },
    [],
    []
];
n0_registry.registerError(exports.NoSuchBucket$, errors_1.NoSuchBucket);
exports.NoSuchKey$ = [-3, n0, _NSK,
    { [_e]: _c, [_hE]: 404 },
    [],
    []
];
n0_registry.registerError(exports.NoSuchKey$, errors_1.NoSuchKey);
exports.NoSuchUpload$ = [-3, n0, _NSU,
    { [_e]: _c, [_hE]: 404 },
    [],
    []
];
n0_registry.registerError(exports.NoSuchUpload$, errors_1.NoSuchUpload);
exports.NotFound$ = [-3, n0, _NF,
    { [_e]: _c },
    [],
    []
];
n0_registry.registerError(exports.NotFound$, errors_1.NotFound);
exports.ObjectAlreadyInActiveTierError$ = [-3, n0, _OAIATE,
    { [_e]: _c, [_hE]: 403 },
    [],
    []
];
n0_registry.registerError(exports.ObjectAlreadyInActiveTierError$, errors_1.ObjectAlreadyInActiveTierError);
exports.ObjectNotInActiveTierError$ = [-3, n0, _ONIATE,
    { [_e]: _c, [_hE]: 403 },
    [],
    []
];
n0_registry.registerError(exports.ObjectNotInActiveTierError$, errors_1.ObjectNotInActiveTierError);
exports.TooManyParts$ = [-3, n0, _TMP,
    { [_e]: _c, [_hE]: 400 },
    [],
    []
];
n0_registry.registerError(exports.TooManyParts$, errors_1.TooManyParts);
exports.errorTypeRegistries = [
    _s_registry,
    n0_registry,
];
var CopySourceSSECustomerKey = [0, n0, _CSSSECK, 8, 0];
var NonEmptyKmsKeyArnString = [0, n0, _NEKKAS, 8, 0];
var SessionCredentialValue = [0, n0, _SCV, 8, 0];
var SSECustomerKey = [0, n0, _SSECK, 8, 0];
var SSEKMSEncryptionContext = [0, n0, _SSEKMSEC, 8, 0];
var SSEKMSKeyId = [0, n0, _SSEKMSKI, 8, 0];
var StreamingBlob = [0, n0, _SB, { [_st]: 1 }, 42];
exports.AbacStatus$ = [3, n0, _AS,
    0,
    [_S],
    [0]
];
exports.AbortIncompleteMultipartUpload$ = [3, n0, _AIMU,
    0,
    [_DAI],
    [1]
];
exports.AbortMultipartUploadOutput$ = [3, n0, _AMUO,
    0,
    [_RC],
    [[0, { [_hH]: _xarc }]]
];
exports.AbortMultipartUploadRequest$ = [3, n0, _AMUR,
    0,
    [_B, _K, _UI, _RP, _EBO, _IMIT],
    [[0, 1], [0, 1], [0, { [_hQ]: _uI }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [6, { [_hH]: _xaimit }]], 3
];
exports.AccelerateConfiguration$ = [3, n0, _AC,
    0,
    [_S],
    [0]
];
exports.AccessControlPolicy$ = [3, n0, _ACP,
    0,
    [_G, _O],
    [[() => Grants, { [_xN]: _ACL }], () => exports.Owner$]
];
exports.AccessControlTranslation$ = [3, n0, _ACT,
    0,
    [_O],
    [0], 1
];
exports.AnalyticsAndOperator$ = [3, n0, _AAO,
    0,
    [_P, _T],
    [0, [() => TagSet, { [_xF]: 1, [_xN]: _Ta }]]
];
exports.AnalyticsConfiguration$ = [3, n0, _ACn,
    0,
    [_I, _SCA, _F],
    [0, () => exports.StorageClassAnalysis$, [() => exports.AnalyticsFilter$, 0]], 2
];
exports.AnalyticsExportDestination$ = [3, n0, _AED,
    0,
    [_SBD],
    [() => exports.AnalyticsS3BucketDestination$], 1
];
exports.AnalyticsS3BucketDestination$ = [3, n0, _ASBD,
    0,
    [_Fo, _B, _BAI, _P],
    [0, 0, 0, 0], 2
];
exports.BlockedEncryptionTypes$ = [3, n0, _BET,
    0,
    [_ET],
    [[() => EncryptionTypeList, { [_xF]: 1 }]]
];
exports.Bucket$ = [3, n0, _B,
    0,
    [_N, _CD, _BR, _BA],
    [0, 4, 0, 0]
];
exports.BucketInfo$ = [3, n0, _BI,
    0,
    [_DR, _Ty],
    [0, 0]
];
exports.BucketLifecycleConfiguration$ = [3, n0, _BLC,
    0,
    [_R],
    [[() => LifecycleRules, { [_xF]: 1, [_xN]: _Ru }]], 1
];
exports.BucketLoggingStatus$ = [3, n0, _BLS,
    0,
    [_LE],
    [[() => exports.LoggingEnabled$, 0]]
];
exports.Checksum$ = [3, n0, _C,
    0,
    [_CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _CT],
    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
];
exports.CommonPrefix$ = [3, n0, _CP,
    0,
    [_P],
    [0]
];
exports.CompletedMultipartUpload$ = [3, n0, _CMU,
    0,
    [_Pa],
    [[() => CompletedPartList, { [_xF]: 1, [_xN]: _Par }]]
];
exports.CompletedPart$ = [3, n0, _CPo,
    0,
    [_ETa, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _PN],
    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]
];
exports.CompleteMultipartUploadOutput$ = [3, n0, _CMUO,
    { [_xN]: _CMUR },
    [_L, _B, _K, _E, _ETa, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _CT, _SSE, _VI, _SSEKMSKI, _BKE, _RC],
    [0, 0, 0, [0, { [_hH]: _xae }], 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, [0, { [_hH]: _xasse }], [0, { [_hH]: _xavi }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarc }]]
];
exports.CompleteMultipartUploadRequest$ = [3, n0, _CMURo,
    0,
    [_B, _K, _UI, _MU, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _CT, _MOS, _RP, _EBO, _IM, _INM, _SSECA, _SSECK, _SSECKMD],
    [[0, 1], [0, 1], [0, { [_hQ]: _uI }], [() => exports.CompletedMultipartUpload$, { [_hP]: 1, [_xN]: _CMUo }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [0, { [_hH]: _xact }], [1, { [_hH]: _xamos }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _IM_ }], [0, { [_hH]: _INM_ }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }]], 3
];
exports.Condition$ = [3, n0, _Co,
    0,
    [_HECRE, _KPE],
    [0, 0]
];
exports.ContinuationEvent$ = [3, n0, _CE,
    0,
    [],
    []
];
exports.CopyObjectOutput$ = [3, n0, _COO,
    0,
    [_COR, _E, _CSVI, _VI, _SSE, _SSECA, _SSECKMD, _SSEKMSKI, _SSEKMSEC, _BKE, _RC],
    [[() => exports.CopyObjectResult$, 16], [0, { [_hH]: _xae }], [0, { [_hH]: _xacsvi }], [0, { [_hH]: _xavi }], [0, { [_hH]: _xasse }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarc }]]
];
exports.CopyObjectRequest$ = [3, n0, _CORo,
    0,
    [_B, _CS, _K, _ACL_, _CC, _CA, _CDo, _CEo, _CL, _CTo, _CSIM, _CSIMS, _CSINM, _CSIUS, _Ex, _GFC, _GR, _GRACP, _GWACP, _IM, _INM, _M, _MD, _TD, _SSE, _SC, _WRL, _SSECA, _SSECK, _SSECKMD, _SSEKMSKI, _SSEKMSEC, _BKE, _CSSSECA, _CSSSECK, _CSSSECKMD, _RP, _Tag, _OLM, _OLRUD, _OLLHS, _EBO, _ESBO],
    [[0, 1], [0, { [_hH]: _xacs___ }], [0, 1], [0, { [_hH]: _xaa }], [0, { [_hH]: _CC_ }], [0, { [_hH]: _xaca }], [0, { [_hH]: _CD_ }], [0, { [_hH]: _CE_ }], [0, { [_hH]: _CL_ }], [0, { [_hH]: _CT_ }], [0, { [_hH]: _xacsim }], [4, { [_hH]: _xacsims }], [0, { [_hH]: _xacsinm }], [4, { [_hH]: _xacsius }], [4, { [_hH]: _Ex }], [0, { [_hH]: _xagfc }], [0, { [_hH]: _xagr }], [0, { [_hH]: _xagra }], [0, { [_hH]: _xagwa }], [0, { [_hH]: _IM_ }], [0, { [_hH]: _INM_ }], [128 | 0, { [_hPH]: _xam }], [0, { [_hH]: _xamd }], [0, { [_hH]: _xatd }], [0, { [_hH]: _xasse }], [0, { [_hH]: _xasc }], [0, { [_hH]: _xawrl }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xacssseca }], [() => CopySourceSSECustomerKey, { [_hH]: _xacssseck }], [0, { [_hH]: _xacssseckM }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xat }], [0, { [_hH]: _xaolm }], [5, { [_hH]: _xaolrud }], [0, { [_hH]: _xaollh }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xasebo }]], 3
];
exports.CopyObjectResult$ = [3, n0, _COR,
    0,
    [_ETa, _LM, _CT, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe],
    [0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
];
exports.CopyPartResult$ = [3, n0, _CPR,
    0,
    [_ETa, _LM, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe],
    [0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
];
exports.CORSConfiguration$ = [3, n0, _CORSC,
    0,
    [_CORSR],
    [[() => CORSRules, { [_xF]: 1, [_xN]: _CORSRu }]], 1
];
exports.CORSRule$ = [3, n0, _CORSRu,
    0,
    [_AM, _AO, _ID, _AH, _EH, _MAS],
    [[64 | 0, { [_xF]: 1, [_xN]: _AMl }], [64 | 0, { [_xF]: 1, [_xN]: _AOl }], 0, [64 | 0, { [_xF]: 1, [_xN]: _AHl }], [64 | 0, { [_xF]: 1, [_xN]: _EHx }], 1], 2
];
exports.CreateBucketConfiguration$ = [3, n0, _CBC,
    0,
    [_LC, _L, _B, _T],
    [0, () => exports.LocationInfo$, () => exports.BucketInfo$, [() => TagSet, 0]]
];
exports.CreateBucketMetadataConfigurationRequest$ = [3, n0, _CBMCR,
    0,
    [_B, _MC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.MetadataConfiguration$, { [_hP]: 1, [_xN]: _MC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.CreateBucketMetadataTableConfigurationRequest$ = [3, n0, _CBMTCR,
    0,
    [_B, _MTC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.MetadataTableConfiguration$, { [_hP]: 1, [_xN]: _MTC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.CreateBucketOutput$ = [3, n0, _CBO,
    0,
    [_L, _BA],
    [[0, { [_hH]: _L }], [0, { [_hH]: _xaba }]]
];
exports.CreateBucketRequest$ = [3, n0, _CBR,
    0,
    [_B, _ACL_, _CBC, _GFC, _GR, _GRACP, _GW, _GWACP, _OLEFB, _OO, _BN],
    [[0, 1], [0, { [_hH]: _xaa }], [() => exports.CreateBucketConfiguration$, { [_hP]: 1, [_xN]: _CBC }], [0, { [_hH]: _xagfc }], [0, { [_hH]: _xagr }], [0, { [_hH]: _xagra }], [0, { [_hH]: _xagw }], [0, { [_hH]: _xagwa }], [2, { [_hH]: _xabole }], [0, { [_hH]: _xaoo }], [0, { [_hH]: _xabn }]], 1
];
exports.CreateMultipartUploadOutput$ = [3, n0, _CMUOr,
    { [_xN]: _IMUR },
    [_ADb, _ARI, _B, _K, _UI, _SSE, _SSECA, _SSECKMD, _SSEKMSKI, _SSEKMSEC, _BKE, _RC, _CA, _CT],
    [[4, { [_hH]: _xaad }], [0, { [_hH]: _xaari }], [0, { [_xN]: _B }], 0, 0, [0, { [_hH]: _xasse }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarc }], [0, { [_hH]: _xaca }], [0, { [_hH]: _xact }]]
];
exports.CreateMultipartUploadRequest$ = [3, n0, _CMURr,
    0,
    [_B, _K, _ACL_, _CC, _CDo, _CEo, _CL, _CTo, _Ex, _GFC, _GR, _GRACP, _GWACP, _M, _SSE, _SC, _WRL, _SSECA, _SSECK, _SSECKMD, _SSEKMSKI, _SSEKMSEC, _BKE, _RP, _Tag, _OLM, _OLRUD, _OLLHS, _EBO, _CA, _CT],
    [[0, 1], [0, 1], [0, { [_hH]: _xaa }], [0, { [_hH]: _CC_ }], [0, { [_hH]: _CD_ }], [0, { [_hH]: _CE_ }], [0, { [_hH]: _CL_ }], [0, { [_hH]: _CT_ }], [4, { [_hH]: _Ex }], [0, { [_hH]: _xagfc }], [0, { [_hH]: _xagr }], [0, { [_hH]: _xagra }], [0, { [_hH]: _xagwa }], [128 | 0, { [_hPH]: _xam }], [0, { [_hH]: _xasse }], [0, { [_hH]: _xasc }], [0, { [_hH]: _xawrl }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xat }], [0, { [_hH]: _xaolm }], [5, { [_hH]: _xaolrud }], [0, { [_hH]: _xaollh }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xaca }], [0, { [_hH]: _xact }]], 2
];
exports.CreateSessionOutput$ = [3, n0, _CSO,
    { [_xN]: _CSR },
    [_Cr, _SSE, _SSEKMSKI, _SSEKMSEC, _BKE],
    [[() => exports.SessionCredentials$, { [_xN]: _Cr }], [0, { [_hH]: _xasse }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }]], 1
];
exports.CreateSessionRequest$ = [3, n0, _CSRr,
    0,
    [_B, _SM, _SSE, _SSEKMSKI, _SSEKMSEC, _BKE],
    [[0, 1], [0, { [_hH]: _xacsm }], [0, { [_hH]: _xasse }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }]], 1
];
exports.CSVInput$ = [3, n0, _CSVIn,
    0,
    [_FHI, _Com, _QEC, _RD, _FD, _QC, _AQRD],
    [0, 0, 0, 0, 0, 0, 2]
];
exports.CSVOutput$ = [3, n0, _CSVO,
    0,
    [_QF, _QEC, _RD, _FD, _QC],
    [0, 0, 0, 0, 0]
];
exports.DefaultRetention$ = [3, n0, _DRe,
    0,
    [_Mo, _D, _Y],
    [0, 1, 1]
];
exports.Delete$ = [3, n0, _De,
    0,
    [_Ob, _Q],
    [[() => ObjectIdentifierList, { [_xF]: 1, [_xN]: _Obj }], 2], 1
];
exports.DeleteBucketAnalyticsConfigurationRequest$ = [3, n0, _DBACR,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.DeleteBucketCorsRequest$ = [3, n0, _DBCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketEncryptionRequest$ = [3, n0, _DBER,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketIntelligentTieringConfigurationRequest$ = [3, n0, _DBITCR,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.DeleteBucketInventoryConfigurationRequest$ = [3, n0, _DBICR,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.DeleteBucketLifecycleRequest$ = [3, n0, _DBLR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketMetadataConfigurationRequest$ = [3, n0, _DBMCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketMetadataTableConfigurationRequest$ = [3, n0, _DBMTCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketMetricsConfigurationRequest$ = [3, n0, _DBMCRe,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.DeleteBucketOwnershipControlsRequest$ = [3, n0, _DBOCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketPolicyRequest$ = [3, n0, _DBPR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketReplicationRequest$ = [3, n0, _DBRR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketRequest$ = [3, n0, _DBR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketTaggingRequest$ = [3, n0, _DBTR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeleteBucketWebsiteRequest$ = [3, n0, _DBWR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.DeletedObject$ = [3, n0, _DO,
    0,
    [_K, _VI, _DM, _DMVI],
    [0, 0, 2, 0]
];
exports.DeleteMarkerEntry$ = [3, n0, _DME,
    0,
    [_O, _K, _VI, _IL, _LM],
    [() => exports.Owner$, 0, 0, 2, 4]
];
exports.DeleteMarkerReplication$ = [3, n0, _DMR,
    0,
    [_S],
    [0]
];
exports.DeleteObjectOutput$ = [3, n0, _DOO,
    0,
    [_DM, _VI, _RC],
    [[2, { [_hH]: _xadm }], [0, { [_hH]: _xavi }], [0, { [_hH]: _xarc }]]
];
exports.DeleteObjectRequest$ = [3, n0, _DOR,
    0,
    [_B, _K, _MFA, _VI, _RP, _BGR, _EBO, _IM, _IMLMT, _IMS],
    [[0, 1], [0, 1], [0, { [_hH]: _xam_ }], [0, { [_hQ]: _vI }], [0, { [_hH]: _xarp }], [2, { [_hH]: _xabgr }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _IM_ }], [6, { [_hH]: _xaimlmt }], [1, { [_hH]: _xaims }]], 2
];
exports.DeleteObjectsOutput$ = [3, n0, _DOOe,
    { [_xN]: _DRel },
    [_Del, _RC, _Er],
    [[() => DeletedObjects, { [_xF]: 1 }], [0, { [_hH]: _xarc }], [() => Errors, { [_xF]: 1, [_xN]: _Err }]]
];
exports.DeleteObjectsRequest$ = [3, n0, _DORe,
    0,
    [_B, _De, _MFA, _RP, _BGR, _EBO, _CA],
    [[0, 1], [() => exports.Delete$, { [_hP]: 1, [_xN]: _De }], [0, { [_hH]: _xam_ }], [0, { [_hH]: _xarp }], [2, { [_hH]: _xabgr }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xasca }]], 2
];
exports.DeleteObjectTaggingOutput$ = [3, n0, _DOTO,
    0,
    [_VI],
    [[0, { [_hH]: _xavi }]]
];
exports.DeleteObjectTaggingRequest$ = [3, n0, _DOTR,
    0,
    [_B, _K, _VI, _EBO],
    [[0, 1], [0, 1], [0, { [_hQ]: _vI }], [0, { [_hH]: _xaebo }]], 2
];
exports.DeletePublicAccessBlockRequest$ = [3, n0, _DPABR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.Destination$ = [3, n0, _Des,
    0,
    [_B, _A, _SC, _ACT, _EC, _RT, _Me],
    [0, 0, 0, () => exports.AccessControlTranslation$, () => exports.EncryptionConfiguration$, () => exports.ReplicationTime$, () => exports.Metrics$], 1
];
exports.DestinationResult$ = [3, n0, _DRes,
    0,
    [_TBT, _TBA, _TN],
    [0, 0, 0]
];
exports.Encryption$ = [3, n0, _En,
    0,
    [_ET, _KMSKI, _KMSC],
    [0, [() => SSEKMSKeyId, 0], 0], 1
];
exports.EncryptionConfiguration$ = [3, n0, _EC,
    0,
    [_RKKID],
    [0]
];
exports.EndEvent$ = [3, n0, _EE,
    0,
    [],
    []
];
exports._Error$ = [3, n0, _Err,
    0,
    [_K, _VI, _Cod, _Mes],
    [0, 0, 0, 0]
];
exports.ErrorDetails$ = [3, n0, _ED,
    0,
    [_ECr, _EM],
    [0, 0]
];
exports.ErrorDocument$ = [3, n0, _EDr,
    0,
    [_K],
    [0], 1
];
exports.EventBridgeConfiguration$ = [3, n0, _EBC,
    0,
    [],
    []
];
exports.ExistingObjectReplication$ = [3, n0, _EOR,
    0,
    [_S],
    [0], 1
];
exports.FilterRule$ = [3, n0, _FR,
    0,
    [_N, _V],
    [0, 0]
];
exports.GetBucketAbacOutput$ = [3, n0, _GBAO,
    0,
    [_AS],
    [[() => exports.AbacStatus$, 16]]
];
exports.GetBucketAbacRequest$ = [3, n0, _GBAR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketAccelerateConfigurationOutput$ = [3, n0, _GBACO,
    { [_xN]: _AC },
    [_S, _RC],
    [0, [0, { [_hH]: _xarc }]]
];
exports.GetBucketAccelerateConfigurationRequest$ = [3, n0, _GBACR,
    0,
    [_B, _EBO, _RP],
    [[0, 1], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xarp }]], 1
];
exports.GetBucketAclOutput$ = [3, n0, _GBAOe,
    { [_xN]: _ACP },
    [_O, _G],
    [() => exports.Owner$, [() => Grants, { [_xN]: _ACL }]]
];
exports.GetBucketAclRequest$ = [3, n0, _GBARe,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketAnalyticsConfigurationOutput$ = [3, n0, _GBACOe,
    0,
    [_ACn],
    [[() => exports.AnalyticsConfiguration$, 16]]
];
exports.GetBucketAnalyticsConfigurationRequest$ = [3, n0, _GBACRe,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetBucketCorsOutput$ = [3, n0, _GBCO,
    { [_xN]: _CORSC },
    [_CORSR],
    [[() => CORSRules, { [_xF]: 1, [_xN]: _CORSRu }]]
];
exports.GetBucketCorsRequest$ = [3, n0, _GBCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketEncryptionOutput$ = [3, n0, _GBEO,
    0,
    [_SSEC],
    [[() => exports.ServerSideEncryptionConfiguration$, 16]]
];
exports.GetBucketEncryptionRequest$ = [3, n0, _GBER,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketIntelligentTieringConfigurationOutput$ = [3, n0, _GBITCO,
    0,
    [_ITC],
    [[() => exports.IntelligentTieringConfiguration$, 16]]
];
exports.GetBucketIntelligentTieringConfigurationRequest$ = [3, n0, _GBITCR,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetBucketInventoryConfigurationOutput$ = [3, n0, _GBICO,
    0,
    [_IC],
    [[() => exports.InventoryConfiguration$, 16]]
];
exports.GetBucketInventoryConfigurationRequest$ = [3, n0, _GBICR,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetBucketLifecycleConfigurationOutput$ = [3, n0, _GBLCO,
    { [_xN]: _LCi },
    [_R, _TDMOS],
    [[() => LifecycleRules, { [_xF]: 1, [_xN]: _Ru }], [0, { [_hH]: _xatdmos }]]
];
exports.GetBucketLifecycleConfigurationRequest$ = [3, n0, _GBLCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketLocationOutput$ = [3, n0, _GBLO,
    { [_xN]: _LC },
    [_LC],
    [0]
];
exports.GetBucketLocationRequest$ = [3, n0, _GBLR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketLoggingOutput$ = [3, n0, _GBLOe,
    { [_xN]: _BLS },
    [_LE],
    [[() => exports.LoggingEnabled$, 0]]
];
exports.GetBucketLoggingRequest$ = [3, n0, _GBLRe,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketMetadataConfigurationOutput$ = [3, n0, _GBMCO,
    0,
    [_GBMCR],
    [[() => exports.GetBucketMetadataConfigurationResult$, 16]]
];
exports.GetBucketMetadataConfigurationRequest$ = [3, n0, _GBMCRe,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketMetadataConfigurationResult$ = [3, n0, _GBMCR,
    0,
    [_MCR],
    [() => exports.MetadataConfigurationResult$], 1
];
exports.GetBucketMetadataTableConfigurationOutput$ = [3, n0, _GBMTCO,
    0,
    [_GBMTCR],
    [[() => exports.GetBucketMetadataTableConfigurationResult$, 16]]
];
exports.GetBucketMetadataTableConfigurationRequest$ = [3, n0, _GBMTCRe,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketMetadataTableConfigurationResult$ = [3, n0, _GBMTCR,
    0,
    [_MTCR, _S, _Err],
    [() => exports.MetadataTableConfigurationResult$, 0, () => exports.ErrorDetails$], 2
];
exports.GetBucketMetricsConfigurationOutput$ = [3, n0, _GBMCOe,
    0,
    [_MCe],
    [[() => exports.MetricsConfiguration$, 16]]
];
exports.GetBucketMetricsConfigurationRequest$ = [3, n0, _GBMCRet,
    0,
    [_B, _I, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetBucketNotificationConfigurationRequest$ = [3, n0, _GBNCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketOwnershipControlsOutput$ = [3, n0, _GBOCO,
    0,
    [_OC],
    [[() => exports.OwnershipControls$, 16]]
];
exports.GetBucketOwnershipControlsRequest$ = [3, n0, _GBOCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketPolicyOutput$ = [3, n0, _GBPO,
    0,
    [_Po],
    [[0, 16]]
];
exports.GetBucketPolicyRequest$ = [3, n0, _GBPR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketPolicyStatusOutput$ = [3, n0, _GBPSO,
    0,
    [_PS],
    [[() => exports.PolicyStatus$, 16]]
];
exports.GetBucketPolicyStatusRequest$ = [3, n0, _GBPSR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketReplicationOutput$ = [3, n0, _GBRO,
    0,
    [_RCe],
    [[() => exports.ReplicationConfiguration$, 16]]
];
exports.GetBucketReplicationRequest$ = [3, n0, _GBRR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketRequestPaymentOutput$ = [3, n0, _GBRPO,
    { [_xN]: _RPC },
    [_Pay],
    [0]
];
exports.GetBucketRequestPaymentRequest$ = [3, n0, _GBRPR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketTaggingOutput$ = [3, n0, _GBTO,
    { [_xN]: _Tag },
    [_TS],
    [[() => TagSet, 0]], 1
];
exports.GetBucketTaggingRequest$ = [3, n0, _GBTR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketVersioningOutput$ = [3, n0, _GBVO,
    { [_xN]: _VC },
    [_S, _MFAD],
    [0, [0, { [_xN]: _MDf }]]
];
exports.GetBucketVersioningRequest$ = [3, n0, _GBVR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetBucketWebsiteOutput$ = [3, n0, _GBWO,
    { [_xN]: _WC },
    [_RART, _IDn, _EDr, _RR],
    [() => exports.RedirectAllRequestsTo$, () => exports.IndexDocument$, () => exports.ErrorDocument$, [() => RoutingRules, 0]]
];
exports.GetBucketWebsiteRequest$ = [3, n0, _GBWR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetObjectAclOutput$ = [3, n0, _GOAO,
    { [_xN]: _ACP },
    [_O, _G, _RC],
    [() => exports.Owner$, [() => Grants, { [_xN]: _ACL }], [0, { [_hH]: _xarc }]]
];
exports.GetObjectAclRequest$ = [3, n0, _GOAR,
    0,
    [_B, _K, _VI, _RP, _EBO],
    [[0, 1], [0, 1], [0, { [_hQ]: _vI }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetObjectAttributesOutput$ = [3, n0, _GOAOe,
    { [_xN]: _GOARe },
    [_DM, _LM, _VI, _RC, _ETa, _C, _OP, _SC, _OS],
    [[2, { [_hH]: _xadm }], [4, { [_hH]: _LM_ }], [0, { [_hH]: _xavi }], [0, { [_hH]: _xarc }], 0, () => exports.Checksum$, [() => exports.GetObjectAttributesParts$, 0], 0, 1]
];
exports.GetObjectAttributesParts$ = [3, n0, _GOAP,
    0,
    [_TPC, _PNM, _NPNM, _MP, _IT, _Pa],
    [[1, { [_xN]: _PC }], 0, 0, 1, 2, [() => PartsList, { [_xF]: 1, [_xN]: _Par }]]
];
exports.GetObjectAttributesRequest$ = [3, n0, _GOARet,
    0,
    [_B, _K, _OA, _VI, _MP, _PNM, _SSECA, _SSECK, _SSECKMD, _RP, _EBO],
    [[0, 1], [0, 1], [64 | 0, { [_hH]: _xaoa }], [0, { [_hQ]: _vI }], [1, { [_hH]: _xamp }], [0, { [_hH]: _xapnm }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }]], 3
];
exports.GetObjectLegalHoldOutput$ = [3, n0, _GOLHO,
    0,
    [_LH],
    [[() => exports.ObjectLockLegalHold$, { [_hP]: 1, [_xN]: _LH }]]
];
exports.GetObjectLegalHoldRequest$ = [3, n0, _GOLHR,
    0,
    [_B, _K, _VI, _RP, _EBO],
    [[0, 1], [0, 1], [0, { [_hQ]: _vI }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetObjectLockConfigurationOutput$ = [3, n0, _GOLCO,
    0,
    [_OLC],
    [[() => exports.ObjectLockConfiguration$, 16]]
];
exports.GetObjectLockConfigurationRequest$ = [3, n0, _GOLCR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GetObjectOutput$ = [3, n0, _GOO,
    0,
    [_Bo, _DM, _AR, _E, _Re, _LM, _CLo, _ETa, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _CT, _MM, _VI, _CC, _CDo, _CEo, _CL, _CR, _CTo, _Ex, _ES, _WRL, _SSE, _M, _SSECA, _SSECKMD, _SSEKMSKI, _BKE, _SC, _RC, _RS, _PC, _TC, _OLM, _OLRUD, _OLLHS],
    [[() => StreamingBlob, 16], [2, { [_hH]: _xadm }], [0, { [_hH]: _ar }], [0, { [_hH]: _xae }], [0, { [_hH]: _xar }], [4, { [_hH]: _LM_ }], [1, { [_hH]: _CL__ }], [0, { [_hH]: _ETa }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [0, { [_hH]: _xact }], [1, { [_hH]: _xamm }], [0, { [_hH]: _xavi }], [0, { [_hH]: _CC_ }], [0, { [_hH]: _CD_ }], [0, { [_hH]: _CE_ }], [0, { [_hH]: _CL_ }], [0, { [_hH]: _CR_ }], [0, { [_hH]: _CT_ }], [4, { [_hH]: _Ex }], [0, { [_hH]: _ES }], [0, { [_hH]: _xawrl }], [0, { [_hH]: _xasse }], [128 | 0, { [_hPH]: _xam }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xasc }], [0, { [_hH]: _xarc }], [0, { [_hH]: _xars }], [1, { [_hH]: _xampc }], [1, { [_hH]: _xatc }], [0, { [_hH]: _xaolm }], [5, { [_hH]: _xaolrud }], [0, { [_hH]: _xaollh }]]
];
exports.GetObjectRequest$ = [3, n0, _GOR,
    0,
    [_B, _K, _IM, _IMSf, _INM, _IUS, _Ra, _RCC, _RCD, _RCE, _RCL, _RCT, _RE, _VI, _SSECA, _SSECK, _SSECKMD, _RP, _PN, _EBO, _CMh],
    [[0, 1], [0, 1], [0, { [_hH]: _IM_ }], [4, { [_hH]: _IMS_ }], [0, { [_hH]: _INM_ }], [4, { [_hH]: _IUS_ }], [0, { [_hH]: _Ra }], [0, { [_hQ]: _rcc }], [0, { [_hQ]: _rcd }], [0, { [_hQ]: _rce }], [0, { [_hQ]: _rcl }], [0, { [_hQ]: _rct }], [6, { [_hQ]: _re }], [0, { [_hQ]: _vI }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [0, { [_hH]: _xarp }], [1, { [_hQ]: _pN }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xacm_ }]], 2
];
exports.GetObjectRetentionOutput$ = [3, n0, _GORO,
    0,
    [_Ret],
    [[() => exports.ObjectLockRetention$, { [_hP]: 1, [_xN]: _Ret }]]
];
exports.GetObjectRetentionRequest$ = [3, n0, _GORR,
    0,
    [_B, _K, _VI, _RP, _EBO],
    [[0, 1], [0, 1], [0, { [_hQ]: _vI }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetObjectTaggingOutput$ = [3, n0, _GOTO,
    { [_xN]: _Tag },
    [_TS, _VI],
    [[() => TagSet, 0], [0, { [_hH]: _xavi }]], 1
];
exports.GetObjectTaggingRequest$ = [3, n0, _GOTR,
    0,
    [_B, _K, _VI, _EBO, _RP],
    [[0, 1], [0, 1], [0, { [_hQ]: _vI }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xarp }]], 2
];
exports.GetObjectTorrentOutput$ = [3, n0, _GOTOe,
    0,
    [_Bo, _RC],
    [[() => StreamingBlob, 16], [0, { [_hH]: _xarc }]]
];
exports.GetObjectTorrentRequest$ = [3, n0, _GOTRe,
    0,
    [_B, _K, _RP, _EBO],
    [[0, 1], [0, 1], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }]], 2
];
exports.GetPublicAccessBlockOutput$ = [3, n0, _GPABO,
    0,
    [_PABC],
    [[() => exports.PublicAccessBlockConfiguration$, 16]]
];
exports.GetPublicAccessBlockRequest$ = [3, n0, _GPABR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.GlacierJobParameters$ = [3, n0, _GJP,
    0,
    [_Ti],
    [0], 1
];
exports.Grant$ = [3, n0, _Gr,
    0,
    [_Gra, _Pe],
    [[() => exports.Grantee$, { [_xNm]: [_x, _hi] }], 0]
];
exports.Grantee$ = [3, n0, _Gra,
    0,
    [_Ty, _DN, _EA, _ID, _URI],
    [[0, { [_xA]: 1, [_xN]: _xs }], 0, 0, 0, 0], 1
];
exports.HeadBucketOutput$ = [3, n0, _HBO,
    0,
    [_BA, _BLT, _BLN, _BR, _APA],
    [[0, { [_hH]: _xaba }], [0, { [_hH]: _xablt }], [0, { [_hH]: _xabln }], [0, { [_hH]: _xabr }], [2, { [_hH]: _xaapa }]]
];
exports.HeadBucketRequest$ = [3, n0, _HBR,
    0,
    [_B, _EBO],
    [[0, 1], [0, { [_hH]: _xaebo }]], 1
];
exports.HeadObjectOutput$ = [3, n0, _HOO,
    0,
    [_DM, _AR, _E, _Re, _ASr, _LM, _CLo, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _CT, _ETa, _MM, _VI, _CC, _CDo, _CEo, _CL, _CTo, _CR, _Ex, _ES, _WRL, _SSE, _M, _SSECA, _SSECKMD, _SSEKMSKI, _BKE, _SC, _RC, _RS, _PC, _TC, _OLM, _OLRUD, _OLLHS],
    [[2, { [_hH]: _xadm }], [0, { [_hH]: _ar }], [0, { [_hH]: _xae }], [0, { [_hH]: _xar }], [0, { [_hH]: _xaas }], [4, { [_hH]: _LM_ }], [1, { [_hH]: _CL__ }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [0, { [_hH]: _xact }], [0, { [_hH]: _ETa }], [1, { [_hH]: _xamm }], [0, { [_hH]: _xavi }], [0, { [_hH]: _CC_ }], [0, { [_hH]: _CD_ }], [0, { [_hH]: _CE_ }], [0, { [_hH]: _CL_ }], [0, { [_hH]: _CT_ }], [0, { [_hH]: _CR_ }], [4, { [_hH]: _Ex }], [0, { [_hH]: _ES }], [0, { [_hH]: _xawrl }], [0, { [_hH]: _xasse }], [128 | 0, { [_hPH]: _xam }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xasc }], [0, { [_hH]: _xarc }], [0, { [_hH]: _xars }], [1, { [_hH]: _xampc }], [1, { [_hH]: _xatc }], [0, { [_hH]: _xaolm }], [5, { [_hH]: _xaolrud }], [0, { [_hH]: _xaollh }]]
];
exports.HeadObjectRequest$ = [3, n0, _HOR,
    0,
    [_B, _K, _IM, _IMSf, _INM, _IUS, _Ra, _RCC, _RCD, _RCE, _RCL, _RCT, _RE, _VI, _SSECA, _SSECK, _SSECKMD, _RP, _PN, _EBO, _CMh],
    [[0, 1], [0, 1], [0, { [_hH]: _IM_ }], [4, { [_hH]: _IMS_ }], [0, { [_hH]: _INM_ }], [4, { [_hH]: _IUS_ }], [0, { [_hH]: _Ra }], [0, { [_hQ]: _rcc }], [0, { [_hQ]: _rcd }], [0, { [_hQ]: _rce }], [0, { [_hQ]: _rcl }], [0, { [_hQ]: _rct }], [6, { [_hQ]: _re }], [0, { [_hQ]: _vI }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [0, { [_hH]: _xarp }], [1, { [_hQ]: _pN }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xacm_ }]], 2
];
exports.IndexDocument$ = [3, n0, _IDn,
    0,
    [_Su],
    [0], 1
];
exports.Initiator$ = [3, n0, _In,
    0,
    [_ID, _DN],
    [0, 0]
];
exports.InputSerialization$ = [3, n0, _IS,
    0,
    [_CSV, _CTom, _JSON, _Parq],
    [() => exports.CSVInput$, 0, () => exports.JSONInput$, () => exports.ParquetInput$]
];
exports.IntelligentTieringAndOperator$ = [3, n0, _ITAO,
    0,
    [_P, _T],
    [0, [() => TagSet, { [_xF]: 1, [_xN]: _Ta }]]
];
exports.IntelligentTieringConfiguration$ = [3, n0, _ITC,
    0,
    [_I, _S, _Tie, _F],
    [0, 0, [() => TieringList, { [_xF]: 1, [_xN]: _Tier }], [() => exports.IntelligentTieringFilter$, 0]], 3
];
exports.IntelligentTieringFilter$ = [3, n0, _ITF,
    0,
    [_P, _Ta, _An],
    [0, () => exports.Tag$, [() => exports.IntelligentTieringAndOperator$, 0]]
];
exports.InventoryConfiguration$ = [3, n0, _IC,
    0,
    [_Des, _IE, _I, _IOV, _Sc, _F, _OF],
    [[() => exports.InventoryDestination$, 0], 2, 0, 0, () => exports.InventorySchedule$, () => exports.InventoryFilter$, [() => InventoryOptionalFields, 0]], 5
];
exports.InventoryDestination$ = [3, n0, _IDnv,
    0,
    [_SBD],
    [[() => exports.InventoryS3BucketDestination$, 0]], 1
];
exports.InventoryEncryption$ = [3, n0, _IEn,
    0,
    [_SSES, _SSEKMS],
    [[() => exports.SSES3$, { [_xN]: _SS }], [() => exports.SSEKMS$, { [_xN]: _SK }]]
];
exports.InventoryFilter$ = [3, n0, _IF,
    0,
    [_P],
    [0], 1
];
exports.InventoryS3BucketDestination$ = [3, n0, _ISBD,
    0,
    [_B, _Fo, _AI, _P, _En],
    [0, 0, 0, 0, [() => exports.InventoryEncryption$, 0]], 2
];
exports.InventorySchedule$ = [3, n0, _ISn,
    0,
    [_Fr],
    [0], 1
];
exports.InventoryTableConfiguration$ = [3, n0, _ITCn,
    0,
    [_CSo, _EC],
    [0, () => exports.MetadataTableEncryptionConfiguration$], 1
];
exports.InventoryTableConfigurationResult$ = [3, n0, _ITCR,
    0,
    [_CSo, _TSa, _Err, _TNa, _TA],
    [0, 0, () => exports.ErrorDetails$, 0, 0], 1
];
exports.InventoryTableConfigurationUpdates$ = [3, n0, _ITCU,
    0,
    [_CSo, _EC],
    [0, () => exports.MetadataTableEncryptionConfiguration$], 1
];
exports.JournalTableConfiguration$ = [3, n0, _JTC,
    0,
    [_REe, _EC],
    [() => exports.RecordExpiration$, () => exports.MetadataTableEncryptionConfiguration$], 1
];
exports.JournalTableConfigurationResult$ = [3, n0, _JTCR,
    0,
    [_TSa, _TNa, _REe, _Err, _TA],
    [0, 0, () => exports.RecordExpiration$, () => exports.ErrorDetails$, 0], 3
];
exports.JournalTableConfigurationUpdates$ = [3, n0, _JTCU,
    0,
    [_REe],
    [() => exports.RecordExpiration$], 1
];
exports.JSONInput$ = [3, n0, _JSONI,
    0,
    [_Ty],
    [0]
];
exports.JSONOutput$ = [3, n0, _JSONO,
    0,
    [_RD],
    [0]
];
exports.LambdaFunctionConfiguration$ = [3, n0, _LFC,
    0,
    [_LFA, _Ev, _I, _F],
    [[0, { [_xN]: _CF }], [64 | 0, { [_xF]: 1, [_xN]: _Eve }], 0, [() => exports.NotificationConfigurationFilter$, 0]], 2
];
exports.LifecycleExpiration$ = [3, n0, _LEi,
    0,
    [_Da, _D, _EODM],
    [5, 1, 2]
];
exports.LifecycleRule$ = [3, n0, _LR,
    0,
    [_S, _E, _ID, _P, _F, _Tr, _NVT, _NVE, _AIMU],
    [0, () => exports.LifecycleExpiration$, 0, 0, [() => exports.LifecycleRuleFilter$, 0], [() => TransitionList, { [_xF]: 1, [_xN]: _Tra }], [() => NoncurrentVersionTransitionList, { [_xF]: 1, [_xN]: _NVTo }], () => exports.NoncurrentVersionExpiration$, () => exports.AbortIncompleteMultipartUpload$], 1
];
exports.LifecycleRuleAndOperator$ = [3, n0, _LRAO,
    0,
    [_P, _T, _OSGT, _OSLT],
    [0, [() => TagSet, { [_xF]: 1, [_xN]: _Ta }], 1, 1]
];
exports.LifecycleRuleFilter$ = [3, n0, _LRF,
    0,
    [_P, _Ta, _OSGT, _OSLT, _An],
    [0, () => exports.Tag$, 1, 1, [() => exports.LifecycleRuleAndOperator$, 0]]
];
exports.ListBucketAnalyticsConfigurationsOutput$ = [3, n0, _LBACO,
    { [_xN]: _LBACR },
    [_IT, _CTon, _NCT, _ACLn],
    [2, 0, 0, [() => AnalyticsConfigurationList, { [_xF]: 1, [_xN]: _ACn }]]
];
exports.ListBucketAnalyticsConfigurationsRequest$ = [3, n0, _LBACRi,
    0,
    [_B, _CTon, _EBO],
    [[0, 1], [0, { [_hQ]: _ct }], [0, { [_hH]: _xaebo }]], 1
];
exports.ListBucketIntelligentTieringConfigurationsOutput$ = [3, n0, _LBITCO,
    0,
    [_IT, _CTon, _NCT, _ITCL],
    [2, 0, 0, [() => IntelligentTieringConfigurationList, { [_xF]: 1, [_xN]: _ITC }]]
];
exports.ListBucketIntelligentTieringConfigurationsRequest$ = [3, n0, _LBITCR,
    0,
    [_B, _CTon, _EBO],
    [[0, 1], [0, { [_hQ]: _ct }], [0, { [_hH]: _xaebo }]], 1
];
exports.ListBucketInventoryConfigurationsOutput$ = [3, n0, _LBICO,
    { [_xN]: _LICR },
    [_CTon, _ICL, _IT, _NCT],
    [0, [() => InventoryConfigurationList, { [_xF]: 1, [_xN]: _IC }], 2, 0]
];
exports.ListBucketInventoryConfigurationsRequest$ = [3, n0, _LBICR,
    0,
    [_B, _CTon, _EBO],
    [[0, 1], [0, { [_hQ]: _ct }], [0, { [_hH]: _xaebo }]], 1
];
exports.ListBucketMetricsConfigurationsOutput$ = [3, n0, _LBMCO,
    { [_xN]: _LMCR },
    [_IT, _CTon, _NCT, _MCL],
    [2, 0, 0, [() => MetricsConfigurationList, { [_xF]: 1, [_xN]: _MCe }]]
];
exports.ListBucketMetricsConfigurationsRequest$ = [3, n0, _LBMCR,
    0,
    [_B, _CTon, _EBO],
    [[0, 1], [0, { [_hQ]: _ct }], [0, { [_hH]: _xaebo }]], 1
];
exports.ListBucketsOutput$ = [3, n0, _LBO,
    { [_xN]: _LAMBR },
    [_Bu, _O, _CTon, _P],
    [[() => Buckets, 0], () => exports.Owner$, 0, 0]
];
exports.ListBucketsRequest$ = [3, n0, _LBR,
    0,
    [_MB, _CTon, _P, _BR],
    [[1, { [_hQ]: _mb }], [0, { [_hQ]: _ct }], [0, { [_hQ]: _p }], [0, { [_hQ]: _br }]]
];
exports.ListDirectoryBucketsOutput$ = [3, n0, _LDBO,
    { [_xN]: _LAMDBR },
    [_Bu, _CTon],
    [[() => Buckets, 0], 0]
];
exports.ListDirectoryBucketsRequest$ = [3, n0, _LDBR,
    0,
    [_CTon, _MDB],
    [[0, { [_hQ]: _ct }], [1, { [_hQ]: _mdb }]]
];
exports.ListMultipartUploadsOutput$ = [3, n0, _LMUO,
    { [_xN]: _LMUR },
    [_B, _KM, _UIM, _NKM, _P, _Deli, _NUIM, _MUa, _IT, _U, _CPom, _ETn, _RC],
    [0, 0, 0, 0, 0, 0, 0, 1, 2, [() => MultipartUploadList, { [_xF]: 1, [_xN]: _Up }], [() => CommonPrefixList, { [_xF]: 1 }], 0, [0, { [_hH]: _xarc }]]
];
exports.ListMultipartUploadsRequest$ = [3, n0, _LMURi,
    0,
    [_B, _Deli, _ETn, _KM, _MUa, _P, _UIM, _EBO, _RP],
    [[0, 1], [0, { [_hQ]: _d }], [0, { [_hQ]: _et }], [0, { [_hQ]: _km }], [1, { [_hQ]: _mu }], [0, { [_hQ]: _p }], [0, { [_hQ]: _uim }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xarp }]], 1
];
exports.ListObjectsOutput$ = [3, n0, _LOO,
    { [_xN]: _LBRi },
    [_IT, _Ma, _NM, _Con, _N, _P, _Deli, _MK, _CPom, _ETn, _RC],
    [2, 0, 0, [() => ObjectList, { [_xF]: 1 }], 0, 0, 0, 1, [() => CommonPrefixList, { [_xF]: 1 }], 0, [0, { [_hH]: _xarc }]]
];
exports.ListObjectsRequest$ = [3, n0, _LOR,
    0,
    [_B, _Deli, _ETn, _Ma, _MK, _P, _RP, _EBO, _OOA],
    [[0, 1], [0, { [_hQ]: _d }], [0, { [_hQ]: _et }], [0, { [_hQ]: _m }], [1, { [_hQ]: _mk }], [0, { [_hQ]: _p }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [64 | 0, { [_hH]: _xaooa }]], 1
];
exports.ListObjectsV2Output$ = [3, n0, _LOVO,
    { [_xN]: _LBRi },
    [_IT, _Con, _N, _P, _Deli, _MK, _CPom, _ETn, _KC, _CTon, _NCT, _SA, _RC],
    [2, [() => ObjectList, { [_xF]: 1 }], 0, 0, 0, 1, [() => CommonPrefixList, { [_xF]: 1 }], 0, 1, 0, 0, 0, [0, { [_hH]: _xarc }]]
];
exports.ListObjectsV2Request$ = [3, n0, _LOVR,
    0,
    [_B, _Deli, _ETn, _MK, _P, _CTon, _FO, _SA, _RP, _EBO, _OOA],
    [[0, 1], [0, { [_hQ]: _d }], [0, { [_hQ]: _et }], [1, { [_hQ]: _mk }], [0, { [_hQ]: _p }], [0, { [_hQ]: _ct }], [2, { [_hQ]: _fo }], [0, { [_hQ]: _sa }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [64 | 0, { [_hH]: _xaooa }]], 1
];
exports.ListObjectVersionsOutput$ = [3, n0, _LOVOi,
    { [_xN]: _LVR },
    [_IT, _KM, _VIM, _NKM, _NVIM, _Ve, _DMe, _N, _P, _Deli, _MK, _CPom, _ETn, _RC],
    [2, 0, 0, 0, 0, [() => ObjectVersionList, { [_xF]: 1, [_xN]: _Ver }], [() => DeleteMarkers, { [_xF]: 1, [_xN]: _DM }], 0, 0, 0, 1, [() => CommonPrefixList, { [_xF]: 1 }], 0, [0, { [_hH]: _xarc }]]
];
exports.ListObjectVersionsRequest$ = [3, n0, _LOVRi,
    0,
    [_B, _Deli, _ETn, _KM, _MK, _P, _VIM, _EBO, _RP, _OOA],
    [[0, 1], [0, { [_hQ]: _d }], [0, { [_hQ]: _et }], [0, { [_hQ]: _km }], [1, { [_hQ]: _mk }], [0, { [_hQ]: _p }], [0, { [_hQ]: _vim }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xarp }], [64 | 0, { [_hH]: _xaooa }]], 1
];
exports.ListPartsOutput$ = [3, n0, _LPO,
    { [_xN]: _LPR },
    [_ADb, _ARI, _B, _K, _UI, _PNM, _NPNM, _MP, _IT, _Pa, _In, _O, _SC, _RC, _CA, _CT],
    [[4, { [_hH]: _xaad }], [0, { [_hH]: _xaari }], 0, 0, 0, 0, 0, 1, 2, [() => Parts, { [_xF]: 1, [_xN]: _Par }], () => exports.Initiator$, () => exports.Owner$, 0, [0, { [_hH]: _xarc }], 0, 0]
];
exports.ListPartsRequest$ = [3, n0, _LPRi,
    0,
    [_B, _K, _UI, _MP, _PNM, _RP, _EBO, _SSECA, _SSECK, _SSECKMD],
    [[0, 1], [0, 1], [0, { [_hQ]: _uI }], [1, { [_hQ]: _mp }], [0, { [_hQ]: _pnm }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }]], 3
];
exports.LocationInfo$ = [3, n0, _LI,
    0,
    [_Ty, _N],
    [0, 0]
];
exports.LoggingEnabled$ = [3, n0, _LE,
    0,
    [_TB, _TP, _TG, _TOKF],
    [0, 0, [() => TargetGrants, 0], [() => exports.TargetObjectKeyFormat$, 0]], 2
];
exports.MetadataConfiguration$ = [3, n0, _MC,
    0,
    [_JTC, _ITCn],
    [() => exports.JournalTableConfiguration$, () => exports.InventoryTableConfiguration$], 1
];
exports.MetadataConfigurationResult$ = [3, n0, _MCR,
    0,
    [_DRes, _JTCR, _ITCR],
    [() => exports.DestinationResult$, () => exports.JournalTableConfigurationResult$, () => exports.InventoryTableConfigurationResult$], 1
];
exports.MetadataEntry$ = [3, n0, _ME,
    0,
    [_N, _V],
    [0, 0]
];
exports.MetadataTableConfiguration$ = [3, n0, _MTC,
    0,
    [_STD],
    [() => exports.S3TablesDestination$], 1
];
exports.MetadataTableConfigurationResult$ = [3, n0, _MTCR,
    0,
    [_STDR],
    [() => exports.S3TablesDestinationResult$], 1
];
exports.MetadataTableEncryptionConfiguration$ = [3, n0, _MTEC,
    0,
    [_SAs, _KKA],
    [0, 0], 1
];
exports.Metrics$ = [3, n0, _Me,
    0,
    [_S, _ETv],
    [0, () => exports.ReplicationTimeValue$], 1
];
exports.MetricsAndOperator$ = [3, n0, _MAO,
    0,
    [_P, _T, _APAc],
    [0, [() => TagSet, { [_xF]: 1, [_xN]: _Ta }], 0]
];
exports.MetricsConfiguration$ = [3, n0, _MCe,
    0,
    [_I, _F],
    [0, [() => exports.MetricsFilter$, 0]], 1
];
exports.MultipartUpload$ = [3, n0, _MU,
    0,
    [_UI, _K, _Ini, _SC, _O, _In, _CA, _CT],
    [0, 0, 4, 0, () => exports.Owner$, () => exports.Initiator$, 0, 0]
];
exports.NoncurrentVersionExpiration$ = [3, n0, _NVE,
    0,
    [_ND, _NNV],
    [1, 1]
];
exports.NoncurrentVersionTransition$ = [3, n0, _NVTo,
    0,
    [_ND, _SC, _NNV],
    [1, 0, 1]
];
exports.NotificationConfiguration$ = [3, n0, _NC,
    0,
    [_TCo, _QCu, _LFCa, _EBC],
    [[() => TopicConfigurationList, { [_xF]: 1, [_xN]: _TCop }], [() => QueueConfigurationList, { [_xF]: 1, [_xN]: _QCue }], [() => LambdaFunctionConfigurationList, { [_xF]: 1, [_xN]: _CFC }], () => exports.EventBridgeConfiguration$]
];
exports.NotificationConfigurationFilter$ = [3, n0, _NCF,
    0,
    [_K],
    [[() => exports.S3KeyFilter$, { [_xN]: _SKe }]]
];
exports._Object$ = [3, n0, _Obj,
    0,
    [_K, _LM, _ETa, _CA, _CT, _Si, _SC, _O, _RSe],
    [0, 4, 0, [64 | 0, { [_xF]: 1 }], 0, 1, 0, () => exports.Owner$, () => exports.RestoreStatus$]
];
exports.ObjectIdentifier$ = [3, n0, _OI,
    0,
    [_K, _VI, _ETa, _LMT, _Si],
    [0, 0, 0, 6, 1], 1
];
exports.ObjectLockConfiguration$ = [3, n0, _OLC,
    0,
    [_OLE, _Ru],
    [0, () => exports.ObjectLockRule$]
];
exports.ObjectLockLegalHold$ = [3, n0, _OLLH,
    0,
    [_S],
    [0]
];
exports.ObjectLockRetention$ = [3, n0, _OLR,
    0,
    [_Mo, _RUD],
    [0, 5]
];
exports.ObjectLockRule$ = [3, n0, _OLRb,
    0,
    [_DRe],
    [() => exports.DefaultRetention$]
];
exports.ObjectPart$ = [3, n0, _OPb,
    0,
    [_PN, _Si, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe],
    [1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
];
exports.ObjectVersion$ = [3, n0, _OV,
    0,
    [_ETa, _CA, _CT, _Si, _SC, _K, _VI, _IL, _LM, _O, _RSe],
    [0, [64 | 0, { [_xF]: 1 }], 0, 1, 0, 0, 0, 2, 4, () => exports.Owner$, () => exports.RestoreStatus$]
];
exports.OutputLocation$ = [3, n0, _OL,
    0,
    [_S_],
    [[() => exports.S3Location$, 0]]
];
exports.OutputSerialization$ = [3, n0, _OSu,
    0,
    [_CSV, _JSON],
    [() => exports.CSVOutput$, () => exports.JSONOutput$]
];
exports.Owner$ = [3, n0, _O,
    0,
    [_DN, _ID],
    [0, 0]
];
exports.OwnershipControls$ = [3, n0, _OC,
    0,
    [_R],
    [[() => OwnershipControlsRules, { [_xF]: 1, [_xN]: _Ru }]], 1
];
exports.OwnershipControlsRule$ = [3, n0, _OCR,
    0,
    [_OO],
    [0], 1
];
exports.ParquetInput$ = [3, n0, _PI,
    0,
    [],
    []
];
exports.Part$ = [3, n0, _Par,
    0,
    [_PN, _LM, _ETa, _Si, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe],
    [1, 4, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
];
exports.PartitionedPrefix$ = [3, n0, _PP,
    { [_xN]: _PP },
    [_PDS],
    [0]
];
exports.PolicyStatus$ = [3, n0, _PS,
    0,
    [_IP],
    [[2, { [_xN]: _IP }]]
];
exports.Progress$ = [3, n0, _Pr,
    0,
    [_BS, _BP, _BRy],
    [1, 1, 1]
];
exports.ProgressEvent$ = [3, n0, _PE,
    0,
    [_Det],
    [[() => exports.Progress$, { [_eP]: 1 }]]
];
exports.PublicAccessBlockConfiguration$ = [3, n0, _PABC,
    0,
    [_BPA, _IPA, _BPP, _RPB],
    [[2, { [_xN]: _BPA }], [2, { [_xN]: _IPA }], [2, { [_xN]: _BPP }], [2, { [_xN]: _RPB }]]
];
exports.PutBucketAbacRequest$ = [3, n0, _PBAR,
    0,
    [_B, _AS, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.AbacStatus$, { [_hP]: 1, [_xN]: _AS }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketAccelerateConfigurationRequest$ = [3, n0, _PBACR,
    0,
    [_B, _AC, _EBO, _CA],
    [[0, 1], [() => exports.AccelerateConfiguration$, { [_hP]: 1, [_xN]: _AC }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xasca }]], 2
];
exports.PutBucketAclRequest$ = [3, n0, _PBARu,
    0,
    [_B, _ACL_, _ACP, _CMDo, _CA, _GFC, _GR, _GRACP, _GW, _GWACP, _EBO],
    [[0, 1], [0, { [_hH]: _xaa }], [() => exports.AccessControlPolicy$, { [_hP]: 1, [_xN]: _ACP }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xagfc }], [0, { [_hH]: _xagr }], [0, { [_hH]: _xagra }], [0, { [_hH]: _xagw }], [0, { [_hH]: _xagwa }], [0, { [_hH]: _xaebo }]], 1
];
exports.PutBucketAnalyticsConfigurationRequest$ = [3, n0, _PBACRu,
    0,
    [_B, _I, _ACn, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [() => exports.AnalyticsConfiguration$, { [_hP]: 1, [_xN]: _ACn }], [0, { [_hH]: _xaebo }]], 3
];
exports.PutBucketCorsRequest$ = [3, n0, _PBCR,
    0,
    [_B, _CORSC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.CORSConfiguration$, { [_hP]: 1, [_xN]: _CORSC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketEncryptionRequest$ = [3, n0, _PBER,
    0,
    [_B, _SSEC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.ServerSideEncryptionConfiguration$, { [_hP]: 1, [_xN]: _SSEC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketIntelligentTieringConfigurationRequest$ = [3, n0, _PBITCR,
    0,
    [_B, _I, _ITC, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [() => exports.IntelligentTieringConfiguration$, { [_hP]: 1, [_xN]: _ITC }], [0, { [_hH]: _xaebo }]], 3
];
exports.PutBucketInventoryConfigurationRequest$ = [3, n0, _PBICR,
    0,
    [_B, _I, _IC, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [() => exports.InventoryConfiguration$, { [_hP]: 1, [_xN]: _IC }], [0, { [_hH]: _xaebo }]], 3
];
exports.PutBucketLifecycleConfigurationOutput$ = [3, n0, _PBLCO,
    0,
    [_TDMOS],
    [[0, { [_hH]: _xatdmos }]]
];
exports.PutBucketLifecycleConfigurationRequest$ = [3, n0, _PBLCR,
    0,
    [_B, _CA, _LCi, _EBO, _TDMOS],
    [[0, 1], [0, { [_hH]: _xasca }], [() => exports.BucketLifecycleConfiguration$, { [_hP]: 1, [_xN]: _LCi }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xatdmos }]], 1
];
exports.PutBucketLoggingRequest$ = [3, n0, _PBLR,
    0,
    [_B, _BLS, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.BucketLoggingStatus$, { [_hP]: 1, [_xN]: _BLS }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketMetricsConfigurationRequest$ = [3, n0, _PBMCR,
    0,
    [_B, _I, _MCe, _EBO],
    [[0, 1], [0, { [_hQ]: _i }], [() => exports.MetricsConfiguration$, { [_hP]: 1, [_xN]: _MCe }], [0, { [_hH]: _xaebo }]], 3
];
exports.PutBucketNotificationConfigurationRequest$ = [3, n0, _PBNCR,
    0,
    [_B, _NC, _EBO, _SDV],
    [[0, 1], [() => exports.NotificationConfiguration$, { [_hP]: 1, [_xN]: _NC }], [0, { [_hH]: _xaebo }], [2, { [_hH]: _xasdv }]], 2
];
exports.PutBucketOwnershipControlsRequest$ = [3, n0, _PBOCR,
    0,
    [_B, _OC, _CMDo, _EBO, _CA],
    [[0, 1], [() => exports.OwnershipControls$, { [_hP]: 1, [_xN]: _OC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xasca }]], 2
];
exports.PutBucketPolicyRequest$ = [3, n0, _PBPR,
    0,
    [_B, _Po, _CMDo, _CA, _CRSBA, _EBO],
    [[0, 1], [0, 16], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [2, { [_hH]: _xacrsba }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketReplicationRequest$ = [3, n0, _PBRR,
    0,
    [_B, _RCe, _CMDo, _CA, _To, _EBO],
    [[0, 1], [() => exports.ReplicationConfiguration$, { [_hP]: 1, [_xN]: _RCe }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xabolt }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketRequestPaymentRequest$ = [3, n0, _PBRPR,
    0,
    [_B, _RPC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.RequestPaymentConfiguration$, { [_hP]: 1, [_xN]: _RPC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketTaggingRequest$ = [3, n0, _PBTR,
    0,
    [_B, _Tag, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.Tagging$, { [_hP]: 1, [_xN]: _Tag }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketVersioningRequest$ = [3, n0, _PBVR,
    0,
    [_B, _VC, _CMDo, _CA, _MFA, _EBO],
    [[0, 1], [() => exports.VersioningConfiguration$, { [_hP]: 1, [_xN]: _VC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xam_ }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutBucketWebsiteRequest$ = [3, n0, _PBWR,
    0,
    [_B, _WC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.WebsiteConfiguration$, { [_hP]: 1, [_xN]: _WC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutObjectAclOutput$ = [3, n0, _POAO,
    0,
    [_RC],
    [[0, { [_hH]: _xarc }]]
];
exports.PutObjectAclRequest$ = [3, n0, _POAR,
    0,
    [_B, _K, _ACL_, _ACP, _CMDo, _CA, _GFC, _GR, _GRACP, _GW, _GWACP, _RP, _VI, _EBO],
    [[0, 1], [0, 1], [0, { [_hH]: _xaa }], [() => exports.AccessControlPolicy$, { [_hP]: 1, [_xN]: _ACP }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xagfc }], [0, { [_hH]: _xagr }], [0, { [_hH]: _xagra }], [0, { [_hH]: _xagw }], [0, { [_hH]: _xagwa }], [0, { [_hH]: _xarp }], [0, { [_hQ]: _vI }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutObjectLegalHoldOutput$ = [3, n0, _POLHO,
    0,
    [_RC],
    [[0, { [_hH]: _xarc }]]
];
exports.PutObjectLegalHoldRequest$ = [3, n0, _POLHR,
    0,
    [_B, _K, _LH, _RP, _VI, _CMDo, _CA, _EBO],
    [[0, 1], [0, 1], [() => exports.ObjectLockLegalHold$, { [_hP]: 1, [_xN]: _LH }], [0, { [_hH]: _xarp }], [0, { [_hQ]: _vI }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutObjectLockConfigurationOutput$ = [3, n0, _POLCO,
    0,
    [_RC],
    [[0, { [_hH]: _xarc }]]
];
exports.PutObjectLockConfigurationRequest$ = [3, n0, _POLCR,
    0,
    [_B, _OLC, _RP, _To, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.ObjectLockConfiguration$, { [_hP]: 1, [_xN]: _OLC }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xabolt }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 1
];
exports.PutObjectOutput$ = [3, n0, _POO,
    0,
    [_E, _ETa, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _CT, _SSE, _VI, _SSECA, _SSECKMD, _SSEKMSKI, _SSEKMSEC, _BKE, _Si, _RC],
    [[0, { [_hH]: _xae }], [0, { [_hH]: _ETa }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [0, { [_hH]: _xact }], [0, { [_hH]: _xasse }], [0, { [_hH]: _xavi }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }], [1, { [_hH]: _xaos }], [0, { [_hH]: _xarc }]]
];
exports.PutObjectRequest$ = [3, n0, _POR,
    0,
    [_B, _K, _ACL_, _Bo, _CC, _CDo, _CEo, _CL, _CLo, _CMDo, _CTo, _CA, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _Ex, _IM, _INM, _GFC, _GR, _GRACP, _GWACP, _WOB, _M, _SSE, _SC, _WRL, _SSECA, _SSECK, _SSECKMD, _SSEKMSKI, _SSEKMSEC, _BKE, _RP, _Tag, _OLM, _OLRUD, _OLLHS, _EBO],
    [[0, 1], [0, 1], [0, { [_hH]: _xaa }], [() => StreamingBlob, 16], [0, { [_hH]: _CC_ }], [0, { [_hH]: _CD_ }], [0, { [_hH]: _CE_ }], [0, { [_hH]: _CL_ }], [1, { [_hH]: _CL__ }], [0, { [_hH]: _CM }], [0, { [_hH]: _CT_ }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [4, { [_hH]: _Ex }], [0, { [_hH]: _IM_ }], [0, { [_hH]: _INM_ }], [0, { [_hH]: _xagfc }], [0, { [_hH]: _xagr }], [0, { [_hH]: _xagra }], [0, { [_hH]: _xagwa }], [1, { [_hH]: _xawob }], [128 | 0, { [_hPH]: _xam }], [0, { [_hH]: _xasse }], [0, { [_hH]: _xasc }], [0, { [_hH]: _xawrl }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [() => SSEKMSEncryptionContext, { [_hH]: _xassec }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xat }], [0, { [_hH]: _xaolm }], [5, { [_hH]: _xaolrud }], [0, { [_hH]: _xaollh }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutObjectRetentionOutput$ = [3, n0, _PORO,
    0,
    [_RC],
    [[0, { [_hH]: _xarc }]]
];
exports.PutObjectRetentionRequest$ = [3, n0, _PORR,
    0,
    [_B, _K, _Ret, _RP, _VI, _BGR, _CMDo, _CA, _EBO],
    [[0, 1], [0, 1], [() => exports.ObjectLockRetention$, { [_hP]: 1, [_xN]: _Ret }], [0, { [_hH]: _xarp }], [0, { [_hQ]: _vI }], [2, { [_hH]: _xabgr }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.PutObjectTaggingOutput$ = [3, n0, _POTO,
    0,
    [_VI],
    [[0, { [_hH]: _xavi }]]
];
exports.PutObjectTaggingRequest$ = [3, n0, _POTR,
    0,
    [_B, _K, _Tag, _VI, _CMDo, _CA, _EBO, _RP],
    [[0, 1], [0, 1], [() => exports.Tagging$, { [_hP]: 1, [_xN]: _Tag }], [0, { [_hQ]: _vI }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xarp }]], 3
];
exports.PutPublicAccessBlockRequest$ = [3, n0, _PPABR,
    0,
    [_B, _PABC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.PublicAccessBlockConfiguration$, { [_hP]: 1, [_xN]: _PABC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.QueueConfiguration$ = [3, n0, _QCue,
    0,
    [_QA, _Ev, _I, _F],
    [[0, { [_xN]: _Qu }], [64 | 0, { [_xF]: 1, [_xN]: _Eve }], 0, [() => exports.NotificationConfigurationFilter$, 0]], 2
];
exports.RecordExpiration$ = [3, n0, _REe,
    0,
    [_E, _D],
    [0, 1], 1
];
exports.RecordsEvent$ = [3, n0, _REec,
    0,
    [_Payl],
    [[21, { [_eP]: 1 }]]
];
exports.Redirect$ = [3, n0, _Red,
    0,
    [_HN, _HRC, _Pro, _RKPW, _RKW],
    [0, 0, 0, 0, 0]
];
exports.RedirectAllRequestsTo$ = [3, n0, _RART,
    0,
    [_HN, _Pro],
    [0, 0], 1
];
exports.RenameObjectOutput$ = [3, n0, _ROO,
    0,
    [],
    []
];
exports.RenameObjectRequest$ = [3, n0, _ROR,
    0,
    [_B, _K, _RSen, _DIM, _DINM, _DIMS, _DIUS, _SIM, _SINM, _SIMS, _SIUS, _CTl],
    [[0, 1], [0, 1], [0, { [_hH]: _xars_ }], [0, { [_hH]: _IM_ }], [0, { [_hH]: _INM_ }], [4, { [_hH]: _IMS_ }], [4, { [_hH]: _IUS_ }], [0, { [_hH]: _xarsim }], [0, { [_hH]: _xarsinm }], [6, { [_hH]: _xarsims }], [6, { [_hH]: _xarsius }], [0, { [_hH]: _xact_, [_iT]: 1 }]], 3
];
exports.ReplicaModifications$ = [3, n0, _RM,
    0,
    [_S],
    [0], 1
];
exports.ReplicationConfiguration$ = [3, n0, _RCe,
    0,
    [_Ro, _R],
    [0, [() => ReplicationRules, { [_xF]: 1, [_xN]: _Ru }]], 2
];
exports.ReplicationRule$ = [3, n0, _RRe,
    0,
    [_S, _Des, _ID, _Pri, _P, _F, _SSC, _EOR, _DMR],
    [0, () => exports.Destination$, 0, 1, 0, [() => exports.ReplicationRuleFilter$, 0], () => exports.SourceSelectionCriteria$, () => exports.ExistingObjectReplication$, () => exports.DeleteMarkerReplication$], 2
];
exports.ReplicationRuleAndOperator$ = [3, n0, _RRAO,
    0,
    [_P, _T],
    [0, [() => TagSet, { [_xF]: 1, [_xN]: _Ta }]]
];
exports.ReplicationRuleFilter$ = [3, n0, _RRF,
    0,
    [_P, _Ta, _An],
    [0, () => exports.Tag$, [() => exports.ReplicationRuleAndOperator$, 0]]
];
exports.ReplicationTime$ = [3, n0, _RT,
    0,
    [_S, _Tim],
    [0, () => exports.ReplicationTimeValue$], 2
];
exports.ReplicationTimeValue$ = [3, n0, _RTV,
    0,
    [_Mi],
    [1]
];
exports.RequestPaymentConfiguration$ = [3, n0, _RPC,
    0,
    [_Pay],
    [0], 1
];
exports.RequestProgress$ = [3, n0, _RPe,
    0,
    [_Ena],
    [2]
];
exports.RestoreObjectOutput$ = [3, n0, _ROOe,
    0,
    [_RC, _ROP],
    [[0, { [_hH]: _xarc }], [0, { [_hH]: _xarop }]]
];
exports.RestoreObjectRequest$ = [3, n0, _RORe,
    0,
    [_B, _K, _VI, _RRes, _RP, _CA, _EBO],
    [[0, 1], [0, 1], [0, { [_hQ]: _vI }], [() => exports.RestoreRequest$, { [_hP]: 1, [_xN]: _RRes }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.RestoreRequest$ = [3, n0, _RRes,
    0,
    [_D, _GJP, _Ty, _Ti, _Desc, _SP, _OL],
    [1, () => exports.GlacierJobParameters$, 0, 0, 0, () => exports.SelectParameters$, [() => exports.OutputLocation$, 0]]
];
exports.RestoreStatus$ = [3, n0, _RSe,
    0,
    [_IRIP, _RED],
    [2, 4]
];
exports.RoutingRule$ = [3, n0, _RRo,
    0,
    [_Red, _Co],
    [() => exports.Redirect$, () => exports.Condition$], 1
];
exports.S3KeyFilter$ = [3, n0, _SKF,
    0,
    [_FRi],
    [[() => FilterRuleList, { [_xF]: 1, [_xN]: _FR }]]
];
exports.S3Location$ = [3, n0, _SL,
    0,
    [_BNu, _P, _En, _CACL, _ACL, _Tag, _UM, _SC],
    [0, 0, [() => exports.Encryption$, 0], 0, [() => Grants, 0], [() => exports.Tagging$, 0], [() => UserMetadata, 0], 0], 2
];
exports.S3TablesDestination$ = [3, n0, _STD,
    0,
    [_TBA, _TNa],
    [0, 0], 2
];
exports.S3TablesDestinationResult$ = [3, n0, _STDR,
    0,
    [_TBA, _TNa, _TA, _TN],
    [0, 0, 0, 0], 4
];
exports.ScanRange$ = [3, n0, _SR,
    0,
    [_St, _End],
    [1, 1]
];
exports.SelectObjectContentOutput$ = [3, n0, _SOCO,
    0,
    [_Payl],
    [[() => exports.SelectObjectContentEventStream$, 16]]
];
exports.SelectObjectContentRequest$ = [3, n0, _SOCR,
    0,
    [_B, _K, _Exp, _ETx, _IS, _OSu, _SSECA, _SSECK, _SSECKMD, _RPe, _SR, _EBO],
    [[0, 1], [0, 1], 0, 0, () => exports.InputSerialization$, () => exports.OutputSerialization$, [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], () => exports.RequestProgress$, () => exports.ScanRange$, [0, { [_hH]: _xaebo }]], 6
];
exports.SelectParameters$ = [3, n0, _SP,
    0,
    [_IS, _ETx, _Exp, _OSu],
    [() => exports.InputSerialization$, 0, 0, () => exports.OutputSerialization$], 4
];
exports.ServerSideEncryptionByDefault$ = [3, n0, _SSEBD,
    0,
    [_SSEA, _KMSMKID],
    [0, [() => SSEKMSKeyId, 0]], 1
];
exports.ServerSideEncryptionConfiguration$ = [3, n0, _SSEC,
    0,
    [_R],
    [[() => ServerSideEncryptionRules, { [_xF]: 1, [_xN]: _Ru }]], 1
];
exports.ServerSideEncryptionRule$ = [3, n0, _SSER,
    0,
    [_ASSEBD, _BKE, _BET],
    [[() => exports.ServerSideEncryptionByDefault$, 0], 2, [() => exports.BlockedEncryptionTypes$, 0]]
];
exports.SessionCredentials$ = [3, n0, _SCe,
    0,
    [_AKI, _SAK, _ST, _E],
    [[0, { [_xN]: _AKI }], [() => SessionCredentialValue, { [_xN]: _SAK }], [() => SessionCredentialValue, { [_xN]: _ST }], [4, { [_xN]: _E }]], 4
];
exports.SimplePrefix$ = [3, n0, _SPi,
    { [_xN]: _SPi },
    [],
    []
];
exports.SourceSelectionCriteria$ = [3, n0, _SSC,
    0,
    [_SKEO, _RM],
    [() => exports.SseKmsEncryptedObjects$, () => exports.ReplicaModifications$]
];
exports.SSEKMS$ = [3, n0, _SSEKMS,
    { [_xN]: _SK },
    [_KI],
    [[() => SSEKMSKeyId, 0]], 1
];
exports.SseKmsEncryptedObjects$ = [3, n0, _SKEO,
    0,
    [_S],
    [0], 1
];
exports.SSEKMSEncryption$ = [3, n0, _SSEKMSE,
    { [_xN]: _SK },
    [_KMSKA, _BKE],
    [[() => NonEmptyKmsKeyArnString, 0], 2], 1
];
exports.SSES3$ = [3, n0, _SSES,
    { [_xN]: _SS },
    [],
    []
];
exports.Stats$ = [3, n0, _Sta,
    0,
    [_BS, _BP, _BRy],
    [1, 1, 1]
];
exports.StatsEvent$ = [3, n0, _SE,
    0,
    [_Det],
    [[() => exports.Stats$, { [_eP]: 1 }]]
];
exports.StorageClassAnalysis$ = [3, n0, _SCA,
    0,
    [_DE],
    [() => exports.StorageClassAnalysisDataExport$]
];
exports.StorageClassAnalysisDataExport$ = [3, n0, _SCADE,
    0,
    [_OSV, _Des],
    [0, () => exports.AnalyticsExportDestination$], 2
];
exports.Tag$ = [3, n0, _Ta,
    0,
    [_K, _V],
    [0, 0], 2
];
exports.Tagging$ = [3, n0, _Tag,
    0,
    [_TS],
    [[() => TagSet, 0]], 1
];
exports.TargetGrant$ = [3, n0, _TGa,
    0,
    [_Gra, _Pe],
    [[() => exports.Grantee$, { [_xNm]: [_x, _hi] }], 0]
];
exports.TargetObjectKeyFormat$ = [3, n0, _TOKF,
    0,
    [_SPi, _PP],
    [[() => exports.SimplePrefix$, { [_xN]: _SPi }], [() => exports.PartitionedPrefix$, { [_xN]: _PP }]]
];
exports.Tiering$ = [3, n0, _Tier,
    0,
    [_D, _AT],
    [1, 0], 2
];
exports.TopicConfiguration$ = [3, n0, _TCop,
    0,
    [_TAo, _Ev, _I, _F],
    [[0, { [_xN]: _Top }], [64 | 0, { [_xF]: 1, [_xN]: _Eve }], 0, [() => exports.NotificationConfigurationFilter$, 0]], 2
];
exports.Transition$ = [3, n0, _Tra,
    0,
    [_Da, _D, _SC],
    [5, 1, 0]
];
exports.UpdateBucketMetadataInventoryTableConfigurationRequest$ = [3, n0, _UBMITCR,
    0,
    [_B, _ITCn, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.InventoryTableConfigurationUpdates$, { [_hP]: 1, [_xN]: _ITCn }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.UpdateBucketMetadataJournalTableConfigurationRequest$ = [3, n0, _UBMJTCR,
    0,
    [_B, _JTC, _CMDo, _CA, _EBO],
    [[0, 1], [() => exports.JournalTableConfigurationUpdates$, { [_hP]: 1, [_xN]: _JTC }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xaebo }]], 2
];
exports.UpdateObjectEncryptionRequest$ = [3, n0, _UOER,
    0,
    [_B, _K, _OE, _VI, _RP, _EBO, _CMDo, _CA],
    [[0, 1], [0, 1], [() => exports.ObjectEncryption$, 16], [0, { [_hQ]: _vI }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }]], 3
];
exports.UpdateObjectEncryptionResponse$ = [3, n0, _UOERp,
    0,
    [_RC],
    [[0, { [_hH]: _xarc }]]
];
exports.UploadPartCopyOutput$ = [3, n0, _UPCO,
    0,
    [_CSVI, _CPR, _SSE, _SSECA, _SSECKMD, _SSEKMSKI, _BKE, _RC],
    [[0, { [_hH]: _xacsvi }], [() => exports.CopyPartResult$, 16], [0, { [_hH]: _xasse }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarc }]]
];
exports.UploadPartCopyRequest$ = [3, n0, _UPCR,
    0,
    [_B, _CS, _K, _PN, _UI, _CSIM, _CSIMS, _CSINM, _CSIUS, _CSRo, _SSECA, _SSECK, _SSECKMD, _CSSSECA, _CSSSECK, _CSSSECKMD, _RP, _EBO, _ESBO],
    [[0, 1], [0, { [_hH]: _xacs___ }], [0, 1], [1, { [_hQ]: _pN }], [0, { [_hQ]: _uI }], [0, { [_hH]: _xacsim }], [4, { [_hH]: _xacsims }], [0, { [_hH]: _xacsinm }], [4, { [_hH]: _xacsius }], [0, { [_hH]: _xacsr }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [0, { [_hH]: _xacssseca }], [() => CopySourceSSECustomerKey, { [_hH]: _xacssseck }], [0, { [_hH]: _xacssseckM }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }], [0, { [_hH]: _xasebo }]], 5
];
exports.UploadPartOutput$ = [3, n0, _UPO,
    0,
    [_SSE, _ETa, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _SSECA, _SSECKMD, _SSEKMSKI, _BKE, _RC],
    [[0, { [_hH]: _xasse }], [0, { [_hH]: _ETa }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [0, { [_hH]: _xasseca }], [0, { [_hH]: _xasseckM }], [() => SSEKMSKeyId, { [_hH]: _xasseakki }], [2, { [_hH]: _xassebke }], [0, { [_hH]: _xarc }]]
];
exports.UploadPartRequest$ = [3, n0, _UPR,
    0,
    [_B, _K, _PN, _UI, _Bo, _CLo, _CMDo, _CA, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _SSECA, _SSECK, _SSECKMD, _RP, _EBO],
    [[0, 1], [0, 1], [1, { [_hQ]: _pN }], [0, { [_hQ]: _uI }], [() => StreamingBlob, 16], [1, { [_hH]: _CL__ }], [0, { [_hH]: _CM }], [0, { [_hH]: _xasca }], [0, { [_hH]: _xacc }], [0, { [_hH]: _xacc_ }], [0, { [_hH]: _xacc__ }], [0, { [_hH]: _xacs }], [0, { [_hH]: _xacs_ }], [0, { [_hH]: _xacs__ }], [0, { [_hH]: _xacm }], [0, { [_hH]: _xacx }], [0, { [_hH]: _xacx_ }], [0, { [_hH]: _xacx__ }], [0, { [_hH]: _xasseca }], [() => SSECustomerKey, { [_hH]: _xasseck }], [0, { [_hH]: _xasseckM }], [0, { [_hH]: _xarp }], [0, { [_hH]: _xaebo }]], 4
];
exports.VersioningConfiguration$ = [3, n0, _VC,
    0,
    [_MFAD, _S],
    [[0, { [_xN]: _MDf }], 0]
];
exports.WebsiteConfiguration$ = [3, n0, _WC,
    0,
    [_EDr, _IDn, _RART, _RR],
    [() => exports.ErrorDocument$, () => exports.IndexDocument$, () => exports.RedirectAllRequestsTo$, [() => RoutingRules, 0]]
];
exports.WriteGetObjectResponseRequest$ = [3, n0, _WGORR,
    0,
    [_RReq, _RTe, _Bo, _SCt, _ECr, _EM, _AR, _CC, _CDo, _CEo, _CL, _CLo, _CR, _CTo, _CCRC, _CCRCC, _CCRCNVME, _CSHA, _CSHAh, _CSHAhe, _CMD, _CXXHASH, _CXXHASHh, _CXXHASHhe, _DM, _ETa, _Ex, _E, _LM, _MM, _M, _OLM, _OLLHS, _OLRUD, _PC, _RS, _RC, _Re, _SSE, _SSECA, _SSEKMSKI, _SSECKMD, _SC, _TC, _VI, _BKE],
    [[0, { [_hL]: 1, [_hH]: _xarr }], [0, { [_hH]: _xart }], [() => StreamingBlob, 16], [1, { [_hH]: _xafs }], [0, { [_hH]: _xafec }], [0, { [_hH]: _xafem }], [0, { [_hH]: _xafhar }], [0, { [_hH]: _xafhCC }], [0, { [_hH]: _xafhCD }], [0, { [_hH]: _xafhCE }], [0, { [_hH]: _xafhCL }], [1, { [_hH]: _CL__ }], [0, { [_hH]: _xafhCR }], [0, { [_hH]: _xafhCT }], [0, { [_hH]: _xafhxacc }], [0, { [_hH]: _xafhxacc_ }], [0, { [_hH]: _xafhxacc__ }], [0, { [_hH]: _xafhxacs }], [0, { [_hH]: _xafhxacs_ }], [0, { [_hH]: _xafhxacs__ }], [0, { [_hH]: _xafhxacm }], [0, { [_hH]: _xafhxacx }], [0, { [_hH]: _xafhxacx_ }], [0, { [_hH]: _xafhxacx__ }], [2, { [_hH]: _xafhxadm }], [0, { [_hH]: _xafhE }], [4, { [_hH]: _xafhE_ }], [0, { [_hH]: _xafhxae }], [4, { [_hH]: _xafhLM }], [1, { [_hH]: _xafhxamm }], [128 | 0, { [_hPH]: _xam }], [0, { [_hH]: _xafhxaolm }], [0, { [_hH]: _xafhxaollh }], [5, { [_hH]: _xafhxaolrud }], [1, { [_hH]: _xafhxampc }], [0, { [_hH]: _xafhxars }], [0, { [_hH]: _xafhxarc }], [0, { [_hH]: _xafhxar }], [0, { [_hH]: _xafhxasse }], [0, { [_hH]: _xafhxasseca }], [() => SSEKMSKeyId, { [_hH]: _xafhxasseakki }], [0, { [_hH]: _xafhxasseckM }], [0, { [_hH]: _xafhxasc }], [1, { [_hH]: _xafhxatc }], [0, { [_hH]: _xafhxavi }], [2, { [_hH]: _xafhxassebke }]], 2
];
var __Unit = "unit";
var AllowedHeaders = 64 | 0;
var AllowedMethods = 64 | 0;
var AllowedOrigins = 64 | 0;
var AnalyticsConfigurationList = [1, n0, _ACLn,
    0, [() => exports.AnalyticsConfiguration$,
        0]
];
var Buckets = [1, n0, _Bu,
    0, [() => exports.Bucket$,
        { [_xN]: _B }]
];
var ChecksumAlgorithmList = 64 | 0;
var CommonPrefixList = [1, n0, _CPL,
    0, () => exports.CommonPrefix$
];
var CompletedPartList = [1, n0, _CPLo,
    0, () => exports.CompletedPart$
];
var CORSRules = [1, n0, _CORSR,
    0, [() => exports.CORSRule$,
        0]
];
var DeletedObjects = [1, n0, _DOe,
    0, () => exports.DeletedObject$
];
var DeleteMarkers = [1, n0, _DMe,
    0, () => exports.DeleteMarkerEntry$
];
var EncryptionTypeList = [1, n0, _ETL,
    0, [0,
        { [_xN]: _ET }]
];
var Errors = [1, n0, _Er,
    0, () => exports._Error$
];
var EventList = 64 | 0;
var ExposeHeaders = 64 | 0;
var FilterRuleList = [1, n0, _FRL,
    0, () => exports.FilterRule$
];
var Grants = [1, n0, _G,
    0, [() => exports.Grant$,
        { [_xN]: _Gr }]
];
var IntelligentTieringConfigurationList = [1, n0, _ITCL,
    0, [() => exports.IntelligentTieringConfiguration$,
        0]
];
var InventoryConfigurationList = [1, n0, _ICL,
    0, [() => exports.InventoryConfiguration$,
        0]
];
var InventoryOptionalFields = [1, n0, _IOF,
    0, [0,
        { [_xN]: _Fi }]
];
var LambdaFunctionConfigurationList = [1, n0, _LFCL,
    0, [() => exports.LambdaFunctionConfiguration$,
        0]
];
var LifecycleRules = [1, n0, _LRi,
    0, [() => exports.LifecycleRule$,
        0]
];
var MetricsConfigurationList = [1, n0, _MCL,
    0, [() => exports.MetricsConfiguration$,
        0]
];
var MultipartUploadList = [1, n0, _MUL,
    0, () => exports.MultipartUpload$
];
var NoncurrentVersionTransitionList = [1, n0, _NVTL,
    0, () => exports.NoncurrentVersionTransition$
];
var ObjectAttributesList = 64 | 0;
var ObjectIdentifierList = [1, n0, _OIL,
    0, () => exports.ObjectIdentifier$
];
var ObjectList = [1, n0, _OLb,
    0, [() => exports._Object$,
        0]
];
var ObjectVersionList = [1, n0, _OVL,
    0, [() => exports.ObjectVersion$,
        0]
];
var OptionalObjectAttributesList = 64 | 0;
var OwnershipControlsRules = [1, n0, _OCRw,
    0, () => exports.OwnershipControlsRule$
];
var Parts = [1, n0, _Pa,
    0, () => exports.Part$
];
var PartsList = [1, n0, _PL,
    0, () => exports.ObjectPart$
];
var QueueConfigurationList = [1, n0, _QCL,
    0, [() => exports.QueueConfiguration$,
        0]
];
var ReplicationRules = [1, n0, _RRep,
    0, [() => exports.ReplicationRule$,
        0]
];
var RoutingRules = [1, n0, _RR,
    0, [() => exports.RoutingRule$,
        { [_xN]: _RRo }]
];
var ServerSideEncryptionRules = [1, n0, _SSERe,
    0, [() => exports.ServerSideEncryptionRule$,
        0]
];
var TagSet = [1, n0, _TS,
    0, [() => exports.Tag$,
        { [_xN]: _Ta }]
];
var TargetGrants = [1, n0, _TG,
    0, [() => exports.TargetGrant$,
        { [_xN]: _Gr }]
];
var TieringList = [1, n0, _TL,
    0, () => exports.Tiering$
];
var TopicConfigurationList = [1, n0, _TCL,
    0, [() => exports.TopicConfiguration$,
        0]
];
var TransitionList = [1, n0, _TLr,
    0, () => exports.Transition$
];
var UserMetadata = [1, n0, _UM,
    0, [() => exports.MetadataEntry$,
        { [_xN]: _ME }]
];
var Metadata = 128 | 0;
exports.AnalyticsFilter$ = [4, n0, _AF,
    0,
    [_P, _Ta, _An],
    [0, () => exports.Tag$, [() => exports.AnalyticsAndOperator$, 0]]
];
exports.MetricsFilter$ = [4, n0, _MF,
    0,
    [_P, _Ta, _APAc, _An],
    [0, () => exports.Tag$, 0, [() => exports.MetricsAndOperator$, 0]]
];
exports.ObjectEncryption$ = [4, n0, _OE,
    0,
    [_SSEKMS],
    [[() => exports.SSEKMSEncryption$, { [_xN]: _SK }]]
];
exports.SelectObjectContentEventStream$ = [4, n0, _SOCES,
    { [_st]: 1 },
    [_Rec, _Sta, _Pr, _Cont, _End],
    [[() => exports.RecordsEvent$, 0], [() => exports.StatsEvent$, 0], [() => exports.ProgressEvent$, 0], () => exports.ContinuationEvent$, () => exports.EndEvent$]
];
exports.AbortMultipartUpload$ = [9, n0, _AMU,
    { [_h]: ["DELETE", "/{Key+}?x-id=AbortMultipartUpload", 204] }, () => exports.AbortMultipartUploadRequest$, () => exports.AbortMultipartUploadOutput$
];
exports.CompleteMultipartUpload$ = [9, n0, _CMUo,
    { [_h]: ["POST", "/{Key+}", 200] }, () => exports.CompleteMultipartUploadRequest$, () => exports.CompleteMultipartUploadOutput$
];
exports.CopyObject$ = [9, n0, _CO,
    { [_h]: ["PUT", "/{Key+}?x-id=CopyObject", 200] }, () => exports.CopyObjectRequest$, () => exports.CopyObjectOutput$
];
exports.CreateBucket$ = [9, n0, _CB,
    { [_h]: ["PUT", "/", 200] }, () => exports.CreateBucketRequest$, () => exports.CreateBucketOutput$
];
exports.CreateBucketMetadataConfiguration$ = [9, n0, _CBMC,
    { [_hC]: "-", [_h]: ["POST", "/?metadataConfiguration", 200] }, () => exports.CreateBucketMetadataConfigurationRequest$, () => __Unit
];
exports.CreateBucketMetadataTableConfiguration$ = [9, n0, _CBMTC,
    { [_hC]: "-", [_h]: ["POST", "/?metadataTable", 200] }, () => exports.CreateBucketMetadataTableConfigurationRequest$, () => __Unit
];
exports.CreateMultipartUpload$ = [9, n0, _CMUr,
    { [_h]: ["POST", "/{Key+}?uploads", 200] }, () => exports.CreateMultipartUploadRequest$, () => exports.CreateMultipartUploadOutput$
];
exports.CreateSession$ = [9, n0, _CSr,
    { [_h]: ["GET", "/?session", 200] }, () => exports.CreateSessionRequest$, () => exports.CreateSessionOutput$
];
exports.DeleteBucket$ = [9, n0, _DB,
    { [_h]: ["DELETE", "/", 204] }, () => exports.DeleteBucketRequest$, () => __Unit
];
exports.DeleteBucketAnalyticsConfiguration$ = [9, n0, _DBAC,
    { [_h]: ["DELETE", "/?analytics", 204] }, () => exports.DeleteBucketAnalyticsConfigurationRequest$, () => __Unit
];
exports.DeleteBucketCors$ = [9, n0, _DBC,
    { [_h]: ["DELETE", "/?cors", 204] }, () => exports.DeleteBucketCorsRequest$, () => __Unit
];
exports.DeleteBucketEncryption$ = [9, n0, _DBE,
    { [_h]: ["DELETE", "/?encryption", 204] }, () => exports.DeleteBucketEncryptionRequest$, () => __Unit
];
exports.DeleteBucketIntelligentTieringConfiguration$ = [9, n0, _DBITC,
    { [_h]: ["DELETE", "/?intelligent-tiering", 204] }, () => exports.DeleteBucketIntelligentTieringConfigurationRequest$, () => __Unit
];
exports.DeleteBucketInventoryConfiguration$ = [9, n0, _DBIC,
    { [_h]: ["DELETE", "/?inventory", 204] }, () => exports.DeleteBucketInventoryConfigurationRequest$, () => __Unit
];
exports.DeleteBucketLifecycle$ = [9, n0, _DBL,
    { [_h]: ["DELETE", "/?lifecycle", 204] }, () => exports.DeleteBucketLifecycleRequest$, () => __Unit
];
exports.DeleteBucketMetadataConfiguration$ = [9, n0, _DBMC,
    { [_h]: ["DELETE", "/?metadataConfiguration", 204] }, () => exports.DeleteBucketMetadataConfigurationRequest$, () => __Unit
];
exports.DeleteBucketMetadataTableConfiguration$ = [9, n0, _DBMTC,
    { [_h]: ["DELETE", "/?metadataTable", 204] }, () => exports.DeleteBucketMetadataTableConfigurationRequest$, () => __Unit
];
exports.DeleteBucketMetricsConfiguration$ = [9, n0, _DBMCe,
    { [_h]: ["DELETE", "/?metrics", 204] }, () => exports.DeleteBucketMetricsConfigurationRequest$, () => __Unit
];
exports.DeleteBucketOwnershipControls$ = [9, n0, _DBOC,
    { [_h]: ["DELETE", "/?ownershipControls", 204] }, () => exports.DeleteBucketOwnershipControlsRequest$, () => __Unit
];
exports.DeleteBucketPolicy$ = [9, n0, _DBP,
    { [_h]: ["DELETE", "/?policy", 204] }, () => exports.DeleteBucketPolicyRequest$, () => __Unit
];
exports.DeleteBucketReplication$ = [9, n0, _DBRe,
    { [_h]: ["DELETE", "/?replication", 204] }, () => exports.DeleteBucketReplicationRequest$, () => __Unit
];
exports.DeleteBucketTagging$ = [9, n0, _DBT,
    { [_h]: ["DELETE", "/?tagging", 204] }, () => exports.DeleteBucketTaggingRequest$, () => __Unit
];
exports.DeleteBucketWebsite$ = [9, n0, _DBW,
    { [_h]: ["DELETE", "/?website", 204] }, () => exports.DeleteBucketWebsiteRequest$, () => __Unit
];
exports.DeleteObject$ = [9, n0, _DOel,
    { [_h]: ["DELETE", "/{Key+}?x-id=DeleteObject", 204] }, () => exports.DeleteObjectRequest$, () => exports.DeleteObjectOutput$
];
exports.DeleteObjects$ = [9, n0, _DOele,
    { [_hC]: "-", [_h]: ["POST", "/?delete", 200] }, () => exports.DeleteObjectsRequest$, () => exports.DeleteObjectsOutput$
];
exports.DeleteObjectTagging$ = [9, n0, _DOT,
    { [_h]: ["DELETE", "/{Key+}?tagging", 204] }, () => exports.DeleteObjectTaggingRequest$, () => exports.DeleteObjectTaggingOutput$
];
exports.DeletePublicAccessBlock$ = [9, n0, _DPAB,
    { [_h]: ["DELETE", "/?publicAccessBlock", 204] }, () => exports.DeletePublicAccessBlockRequest$, () => __Unit
];
exports.GetBucketAbac$ = [9, n0, _GBA,
    { [_h]: ["GET", "/?abac", 200] }, () => exports.GetBucketAbacRequest$, () => exports.GetBucketAbacOutput$
];
exports.GetBucketAccelerateConfiguration$ = [9, n0, _GBAC,
    { [_h]: ["GET", "/?accelerate", 200] }, () => exports.GetBucketAccelerateConfigurationRequest$, () => exports.GetBucketAccelerateConfigurationOutput$
];
exports.GetBucketAcl$ = [9, n0, _GBAe,
    { [_h]: ["GET", "/?acl", 200] }, () => exports.GetBucketAclRequest$, () => exports.GetBucketAclOutput$
];
exports.GetBucketAnalyticsConfiguration$ = [9, n0, _GBACe,
    { [_h]: ["GET", "/?analytics&x-id=GetBucketAnalyticsConfiguration", 200] }, () => exports.GetBucketAnalyticsConfigurationRequest$, () => exports.GetBucketAnalyticsConfigurationOutput$
];
exports.GetBucketCors$ = [9, n0, _GBC,
    { [_h]: ["GET", "/?cors", 200] }, () => exports.GetBucketCorsRequest$, () => exports.GetBucketCorsOutput$
];
exports.GetBucketEncryption$ = [9, n0, _GBE,
    { [_h]: ["GET", "/?encryption", 200] }, () => exports.GetBucketEncryptionRequest$, () => exports.GetBucketEncryptionOutput$
];
exports.GetBucketIntelligentTieringConfiguration$ = [9, n0, _GBITC,
    { [_h]: ["GET", "/?intelligent-tiering&x-id=GetBucketIntelligentTieringConfiguration", 200] }, () => exports.GetBucketIntelligentTieringConfigurationRequest$, () => exports.GetBucketIntelligentTieringConfigurationOutput$
];
exports.GetBucketInventoryConfiguration$ = [9, n0, _GBIC,
    { [_h]: ["GET", "/?inventory&x-id=GetBucketInventoryConfiguration", 200] }, () => exports.GetBucketInventoryConfigurationRequest$, () => exports.GetBucketInventoryConfigurationOutput$
];
exports.GetBucketLifecycleConfiguration$ = [9, n0, _GBLC,
    { [_h]: ["GET", "/?lifecycle", 200] }, () => exports.GetBucketLifecycleConfigurationRequest$, () => exports.GetBucketLifecycleConfigurationOutput$
];
exports.GetBucketLocation$ = [9, n0, _GBL,
    { [_h]: ["GET", "/?location", 200] }, () => exports.GetBucketLocationRequest$, () => exports.GetBucketLocationOutput$
];
exports.GetBucketLogging$ = [9, n0, _GBLe,
    { [_h]: ["GET", "/?logging", 200] }, () => exports.GetBucketLoggingRequest$, () => exports.GetBucketLoggingOutput$
];
exports.GetBucketMetadataConfiguration$ = [9, n0, _GBMC,
    { [_h]: ["GET", "/?metadataConfiguration", 200] }, () => exports.GetBucketMetadataConfigurationRequest$, () => exports.GetBucketMetadataConfigurationOutput$
];
exports.GetBucketMetadataTableConfiguration$ = [9, n0, _GBMTC,
    { [_h]: ["GET", "/?metadataTable", 200] }, () => exports.GetBucketMetadataTableConfigurationRequest$, () => exports.GetBucketMetadataTableConfigurationOutput$
];
exports.GetBucketMetricsConfiguration$ = [9, n0, _GBMCe,
    { [_h]: ["GET", "/?metrics&x-id=GetBucketMetricsConfiguration", 200] }, () => exports.GetBucketMetricsConfigurationRequest$, () => exports.GetBucketMetricsConfigurationOutput$
];
exports.GetBucketNotificationConfiguration$ = [9, n0, _GBNC,
    { [_h]: ["GET", "/?notification", 200] }, () => exports.GetBucketNotificationConfigurationRequest$, () => exports.NotificationConfiguration$
];
exports.GetBucketOwnershipControls$ = [9, n0, _GBOC,
    { [_h]: ["GET", "/?ownershipControls", 200] }, () => exports.GetBucketOwnershipControlsRequest$, () => exports.GetBucketOwnershipControlsOutput$
];
exports.GetBucketPolicy$ = [9, n0, _GBP,
    { [_h]: ["GET", "/?policy", 200] }, () => exports.GetBucketPolicyRequest$, () => exports.GetBucketPolicyOutput$
];
exports.GetBucketPolicyStatus$ = [9, n0, _GBPS,
    { [_h]: ["GET", "/?policyStatus", 200] }, () => exports.GetBucketPolicyStatusRequest$, () => exports.GetBucketPolicyStatusOutput$
];
exports.GetBucketReplication$ = [9, n0, _GBR,
    { [_h]: ["GET", "/?replication", 200] }, () => exports.GetBucketReplicationRequest$, () => exports.GetBucketReplicationOutput$
];
exports.GetBucketRequestPayment$ = [9, n0, _GBRP,
    { [_h]: ["GET", "/?requestPayment", 200] }, () => exports.GetBucketRequestPaymentRequest$, () => exports.GetBucketRequestPaymentOutput$
];
exports.GetBucketTagging$ = [9, n0, _GBT,
    { [_h]: ["GET", "/?tagging", 200] }, () => exports.GetBucketTaggingRequest$, () => exports.GetBucketTaggingOutput$
];
exports.GetBucketVersioning$ = [9, n0, _GBV,
    { [_h]: ["GET", "/?versioning", 200] }, () => exports.GetBucketVersioningRequest$, () => exports.GetBucketVersioningOutput$
];
exports.GetBucketWebsite$ = [9, n0, _GBW,
    { [_h]: ["GET", "/?website", 200] }, () => exports.GetBucketWebsiteRequest$, () => exports.GetBucketWebsiteOutput$
];
exports.GetObject$ = [9, n0, _GO,
    { [_hC]: "-", [_h]: ["GET", "/{Key+}?x-id=GetObject", 200] }, () => exports.GetObjectRequest$, () => exports.GetObjectOutput$
];
exports.GetObjectAcl$ = [9, n0, _GOA,
    { [_h]: ["GET", "/{Key+}?acl", 200] }, () => exports.GetObjectAclRequest$, () => exports.GetObjectAclOutput$
];
exports.GetObjectAttributes$ = [9, n0, _GOAe,
    { [_h]: ["GET", "/{Key+}?attributes", 200] }, () => exports.GetObjectAttributesRequest$, () => exports.GetObjectAttributesOutput$
];
exports.GetObjectLegalHold$ = [9, n0, _GOLH,
    { [_h]: ["GET", "/{Key+}?legal-hold", 200] }, () => exports.GetObjectLegalHoldRequest$, () => exports.GetObjectLegalHoldOutput$
];
exports.GetObjectLockConfiguration$ = [9, n0, _GOLC,
    { [_h]: ["GET", "/?object-lock", 200] }, () => exports.GetObjectLockConfigurationRequest$, () => exports.GetObjectLockConfigurationOutput$
];
exports.GetObjectRetention$ = [9, n0, _GORe,
    { [_h]: ["GET", "/{Key+}?retention", 200] }, () => exports.GetObjectRetentionRequest$, () => exports.GetObjectRetentionOutput$
];
exports.GetObjectTagging$ = [9, n0, _GOT,
    { [_h]: ["GET", "/{Key+}?tagging", 200] }, () => exports.GetObjectTaggingRequest$, () => exports.GetObjectTaggingOutput$
];
exports.GetObjectTorrent$ = [9, n0, _GOTe,
    { [_h]: ["GET", "/{Key+}?torrent", 200] }, () => exports.GetObjectTorrentRequest$, () => exports.GetObjectTorrentOutput$
];
exports.GetPublicAccessBlock$ = [9, n0, _GPAB,
    { [_h]: ["GET", "/?publicAccessBlock", 200] }, () => exports.GetPublicAccessBlockRequest$, () => exports.GetPublicAccessBlockOutput$
];
exports.HeadBucket$ = [9, n0, _HB,
    { [_h]: ["HEAD", "/", 200] }, () => exports.HeadBucketRequest$, () => exports.HeadBucketOutput$
];
exports.HeadObject$ = [9, n0, _HO,
    { [_h]: ["HEAD", "/{Key+}", 200] }, () => exports.HeadObjectRequest$, () => exports.HeadObjectOutput$
];
exports.ListBucketAnalyticsConfigurations$ = [9, n0, _LBAC,
    { [_h]: ["GET", "/?analytics&x-id=ListBucketAnalyticsConfigurations", 200] }, () => exports.ListBucketAnalyticsConfigurationsRequest$, () => exports.ListBucketAnalyticsConfigurationsOutput$
];
exports.ListBucketIntelligentTieringConfigurations$ = [9, n0, _LBITC,
    { [_h]: ["GET", "/?intelligent-tiering&x-id=ListBucketIntelligentTieringConfigurations", 200] }, () => exports.ListBucketIntelligentTieringConfigurationsRequest$, () => exports.ListBucketIntelligentTieringConfigurationsOutput$
];
exports.ListBucketInventoryConfigurations$ = [9, n0, _LBIC,
    { [_h]: ["GET", "/?inventory&x-id=ListBucketInventoryConfigurations", 200] }, () => exports.ListBucketInventoryConfigurationsRequest$, () => exports.ListBucketInventoryConfigurationsOutput$
];
exports.ListBucketMetricsConfigurations$ = [9, n0, _LBMC,
    { [_h]: ["GET", "/?metrics&x-id=ListBucketMetricsConfigurations", 200] }, () => exports.ListBucketMetricsConfigurationsRequest$, () => exports.ListBucketMetricsConfigurationsOutput$
];
exports.ListBuckets$ = [9, n0, _LB,
    { [_h]: ["GET", "/?x-id=ListBuckets", 200] }, () => exports.ListBucketsRequest$, () => exports.ListBucketsOutput$
];
exports.ListDirectoryBuckets$ = [9, n0, _LDB,
    { [_h]: ["GET", "/?x-id=ListDirectoryBuckets", 200] }, () => exports.ListDirectoryBucketsRequest$, () => exports.ListDirectoryBucketsOutput$
];
exports.ListMultipartUploads$ = [9, n0, _LMU,
    { [_h]: ["GET", "/?uploads", 200] }, () => exports.ListMultipartUploadsRequest$, () => exports.ListMultipartUploadsOutput$
];
exports.ListObjects$ = [9, n0, _LO,
    { [_h]: ["GET", "/", 200] }, () => exports.ListObjectsRequest$, () => exports.ListObjectsOutput$
];
exports.ListObjectsV2$ = [9, n0, _LOV,
    { [_h]: ["GET", "/?list-type=2", 200] }, () => exports.ListObjectsV2Request$, () => exports.ListObjectsV2Output$
];
exports.ListObjectVersions$ = [9, n0, _LOVi,
    { [_h]: ["GET", "/?versions", 200] }, () => exports.ListObjectVersionsRequest$, () => exports.ListObjectVersionsOutput$
];
exports.ListParts$ = [9, n0, _LP,
    { [_h]: ["GET", "/{Key+}?x-id=ListParts", 200] }, () => exports.ListPartsRequest$, () => exports.ListPartsOutput$
];
exports.PutBucketAbac$ = [9, n0, _PBA,
    { [_hC]: "-", [_h]: ["PUT", "/?abac", 200] }, () => exports.PutBucketAbacRequest$, () => __Unit
];
exports.PutBucketAccelerateConfiguration$ = [9, n0, _PBAC,
    { [_hC]: "-", [_h]: ["PUT", "/?accelerate", 200] }, () => exports.PutBucketAccelerateConfigurationRequest$, () => __Unit
];
exports.PutBucketAcl$ = [9, n0, _PBAu,
    { [_hC]: "-", [_h]: ["PUT", "/?acl", 200] }, () => exports.PutBucketAclRequest$, () => __Unit
];
exports.PutBucketAnalyticsConfiguration$ = [9, n0, _PBACu,
    { [_h]: ["PUT", "/?analytics", 200] }, () => exports.PutBucketAnalyticsConfigurationRequest$, () => __Unit
];
exports.PutBucketCors$ = [9, n0, _PBC,
    { [_hC]: "-", [_h]: ["PUT", "/?cors", 200] }, () => exports.PutBucketCorsRequest$, () => __Unit
];
exports.PutBucketEncryption$ = [9, n0, _PBE,
    { [_hC]: "-", [_h]: ["PUT", "/?encryption", 200] }, () => exports.PutBucketEncryptionRequest$, () => __Unit
];
exports.PutBucketIntelligentTieringConfiguration$ = [9, n0, _PBITC,
    { [_h]: ["PUT", "/?intelligent-tiering", 200] }, () => exports.PutBucketIntelligentTieringConfigurationRequest$, () => __Unit
];
exports.PutBucketInventoryConfiguration$ = [9, n0, _PBIC,
    { [_h]: ["PUT", "/?inventory", 200] }, () => exports.PutBucketInventoryConfigurationRequest$, () => __Unit
];
exports.PutBucketLifecycleConfiguration$ = [9, n0, _PBLC,
    { [_hC]: "-", [_h]: ["PUT", "/?lifecycle", 200] }, () => exports.PutBucketLifecycleConfigurationRequest$, () => exports.PutBucketLifecycleConfigurationOutput$
];
exports.PutBucketLogging$ = [9, n0, _PBL,
    { [_hC]: "-", [_h]: ["PUT", "/?logging", 200] }, () => exports.PutBucketLoggingRequest$, () => __Unit
];
exports.PutBucketMetricsConfiguration$ = [9, n0, _PBMC,
    { [_h]: ["PUT", "/?metrics", 200] }, () => exports.PutBucketMetricsConfigurationRequest$, () => __Unit
];
exports.PutBucketNotificationConfiguration$ = [9, n0, _PBNC,
    { [_h]: ["PUT", "/?notification", 200] }, () => exports.PutBucketNotificationConfigurationRequest$, () => __Unit
];
exports.PutBucketOwnershipControls$ = [9, n0, _PBOC,
    { [_hC]: "-", [_h]: ["PUT", "/?ownershipControls", 200] }, () => exports.PutBucketOwnershipControlsRequest$, () => __Unit
];
exports.PutBucketPolicy$ = [9, n0, _PBP,
    { [_hC]: "-", [_h]: ["PUT", "/?policy", 200] }, () => exports.PutBucketPolicyRequest$, () => __Unit
];
exports.PutBucketReplication$ = [9, n0, _PBR,
    { [_hC]: "-", [_h]: ["PUT", "/?replication", 200] }, () => exports.PutBucketReplicationRequest$, () => __Unit
];
exports.PutBucketRequestPayment$ = [9, n0, _PBRP,
    { [_hC]: "-", [_h]: ["PUT", "/?requestPayment", 200] }, () => exports.PutBucketRequestPaymentRequest$, () => __Unit
];
exports.PutBucketTagging$ = [9, n0, _PBT,
    { [_hC]: "-", [_h]: ["PUT", "/?tagging", 200] }, () => exports.PutBucketTaggingRequest$, () => __Unit
];
exports.PutBucketVersioning$ = [9, n0, _PBV,
    { [_hC]: "-", [_h]: ["PUT", "/?versioning", 200] }, () => exports.PutBucketVersioningRequest$, () => __Unit
];
exports.PutBucketWebsite$ = [9, n0, _PBW,
    { [_hC]: "-", [_h]: ["PUT", "/?website", 200] }, () => exports.PutBucketWebsiteRequest$, () => __Unit
];
exports.PutObject$ = [9, n0, _PO,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?x-id=PutObject", 200] }, () => exports.PutObjectRequest$, () => exports.PutObjectOutput$
];
exports.PutObjectAcl$ = [9, n0, _POA,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?acl", 200] }, () => exports.PutObjectAclRequest$, () => exports.PutObjectAclOutput$
];
exports.PutObjectLegalHold$ = [9, n0, _POLH,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?legal-hold", 200] }, () => exports.PutObjectLegalHoldRequest$, () => exports.PutObjectLegalHoldOutput$
];
exports.PutObjectLockConfiguration$ = [9, n0, _POLC,
    { [_hC]: "-", [_h]: ["PUT", "/?object-lock", 200] }, () => exports.PutObjectLockConfigurationRequest$, () => exports.PutObjectLockConfigurationOutput$
];
exports.PutObjectRetention$ = [9, n0, _PORu,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?retention", 200] }, () => exports.PutObjectRetentionRequest$, () => exports.PutObjectRetentionOutput$
];
exports.PutObjectTagging$ = [9, n0, _POT,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?tagging", 200] }, () => exports.PutObjectTaggingRequest$, () => exports.PutObjectTaggingOutput$
];
exports.PutPublicAccessBlock$ = [9, n0, _PPAB,
    { [_hC]: "-", [_h]: ["PUT", "/?publicAccessBlock", 200] }, () => exports.PutPublicAccessBlockRequest$, () => __Unit
];
exports.RenameObject$ = [9, n0, _RO,
    { [_h]: ["PUT", "/{Key+}?renameObject", 200] }, () => exports.RenameObjectRequest$, () => exports.RenameObjectOutput$
];
exports.RestoreObject$ = [9, n0, _ROe,
    { [_hC]: "-", [_h]: ["POST", "/{Key+}?restore", 200] }, () => exports.RestoreObjectRequest$, () => exports.RestoreObjectOutput$
];
exports.SelectObjectContent$ = [9, n0, _SOC,
    { [_h]: ["POST", "/{Key+}?select&select-type=2", 200] }, () => exports.SelectObjectContentRequest$, () => exports.SelectObjectContentOutput$
];
exports.UpdateBucketMetadataInventoryTableConfiguration$ = [9, n0, _UBMITC,
    { [_hC]: "-", [_h]: ["PUT", "/?metadataInventoryTable", 200] }, () => exports.UpdateBucketMetadataInventoryTableConfigurationRequest$, () => __Unit
];
exports.UpdateBucketMetadataJournalTableConfiguration$ = [9, n0, _UBMJTC,
    { [_hC]: "-", [_h]: ["PUT", "/?metadataJournalTable", 200] }, () => exports.UpdateBucketMetadataJournalTableConfigurationRequest$, () => __Unit
];
exports.UpdateObjectEncryption$ = [9, n0, _UOE,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?encryption", 200] }, () => exports.UpdateObjectEncryptionRequest$, () => exports.UpdateObjectEncryptionResponse$
];
exports.UploadPart$ = [9, n0, _UP,
    { [_hC]: "-", [_h]: ["PUT", "/{Key+}?x-id=UploadPart", 200] }, () => exports.UploadPartRequest$, () => exports.UploadPartOutput$
];
exports.UploadPartCopy$ = [9, n0, _UPC,
    { [_h]: ["PUT", "/{Key+}?x-id=UploadPartCopy", 200] }, () => exports.UploadPartCopyRequest$, () => exports.UploadPartCopyOutput$
];
exports.WriteGetObjectResponse$ = [9, n0, _WGOR,
    { [_en]: ["{RequestRoute}."], [_h]: ["POST", "/WriteGetObjectResponse", 200] }, () => exports.WriteGetObjectResponseRequest$, () => __Unit
];
