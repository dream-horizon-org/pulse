'use strict';

var utilConfigProvider = require('@smithy/util-config-provider');
var utilArnParser = require('@aws-sdk/util-arn-parser');
var protocolHttp = require('@smithy/protocol-http');

const NODE_DISABLE_MULTIREGION_ACCESS_POINT_ENV_NAME = "AWS_S3_DISABLE_MULTIREGION_ACCESS_POINTS";
const NODE_DISABLE_MULTIREGION_ACCESS_POINT_INI_NAME = "s3_disable_multiregion_access_points";
const NODE_DISABLE_MULTIREGION_ACCESS_POINT_CONFIG_OPTIONS = {
    environmentVariableSelector: (env) => utilConfigProvider.booleanSelector(env, NODE_DISABLE_MULTIREGION_ACCESS_POINT_ENV_NAME, utilConfigProvider.SelectorType.ENV),
    configFileSelector: (profile) => utilConfigProvider.booleanSelector(profile, NODE_DISABLE_MULTIREGION_ACCESS_POINT_INI_NAME, utilConfigProvider.SelectorType.CONFIG),
    default: false,
};

const NODE_USE_ARN_REGION_ENV_NAME = "AWS_S3_USE_ARN_REGION";
const NODE_USE_ARN_REGION_INI_NAME = "s3_use_arn_region";
const NODE_USE_ARN_REGION_CONFIG_OPTIONS = {
    environmentVariableSelector: (env) => utilConfigProvider.booleanSelector(env, NODE_USE_ARN_REGION_ENV_NAME, utilConfigProvider.SelectorType.ENV),
    configFileSelector: (profile) => utilConfigProvider.booleanSelector(profile, NODE_USE_ARN_REGION_INI_NAME, utilConfigProvider.SelectorType.CONFIG),
    default: undefined,
};

const DOMAIN_PATTERN = /^[a-z0-9][a-z0-9\.\-]{1,61}[a-z0-9]$/;
const IP_ADDRESS_PATTERN = /(\d+\.){3}\d+/;
const DOTS_PATTERN = /\.\./;
const DOT_PATTERN = /\./;
const S3_HOSTNAME_PATTERN = /^(.+\.)?s3(-fips)?(\.dualstack)?[.-]([a-z0-9-]+)\./;
const S3_US_EAST_1_ALTNAME_PATTERN = /^s3(-external-1)?\.amazonaws\.com$/;
const AWS_PARTITION_SUFFIX = "amazonaws.com";
const isBucketNameOptions = (options) => typeof options.bucketName === "string";
const isDnsCompatibleBucketName = (bucketName) => DOMAIN_PATTERN.test(bucketName) && !IP_ADDRESS_PATTERN.test(bucketName) && !DOTS_PATTERN.test(bucketName);
const getRegionalSuffix = (hostname) => {
    const parts = hostname.match(S3_HOSTNAME_PATTERN);
    return [parts[4], hostname.replace(new RegExp(`^${parts[0]}`), "")];
};
const getSuffix = (hostname) => S3_US_EAST_1_ALTNAME_PATTERN.test(hostname) ? ["us-east-1", AWS_PARTITION_SUFFIX] : getRegionalSuffix(hostname);
const getSuffixForArnEndpoint = (hostname) => S3_US_EAST_1_ALTNAME_PATTERN.test(hostname)
    ? [hostname.replace(`.${AWS_PARTITION_SUFFIX}`, ""), AWS_PARTITION_SUFFIX]
    : getRegionalSuffix(hostname);
const validateArnEndpointOptions = (options) => {
    if (options.pathStyleEndpoint) {
        throw new Error("Path-style S3 endpoint is not supported when bucket is an ARN");
    }
    if (options.accelerateEndpoint) {
        throw new Error("Accelerate endpoint is not supported when bucket is an ARN");
    }
    if (!options.tlsCompatible) {
        throw new Error("HTTPS is required when bucket is an ARN");
    }
};
const validateService = (service) => {
    if (service !== "s3" && service !== "s3-outposts" && service !== "s3-object-lambda") {
        throw new Error("Expect 's3' or 's3-outposts' or 's3-object-lambda' in ARN service component");
    }
};
const validateS3Service = (service) => {
    if (service !== "s3") {
        throw new Error("Expect 's3' in Accesspoint ARN service component");
    }
};
const validateOutpostService = (service) => {
    if (service !== "s3-outposts") {
        throw new Error("Expect 's3-posts' in Outpost ARN service component");
    }
};
const validatePartition = (partition, options) => {
    if (partition !== options.clientPartition) {
        throw new Error(`Partition in ARN is incompatible, got "${partition}" but expected "${options.clientPartition}"`);
    }
};
const validateRegion = (region, options) => { };
const validateRegionalClient = (region) => {
    if (["s3-external-1", "aws-global"].includes(region)) {
        throw new Error(`Client region ${region} is not regional`);
    }
};
const validateAccountId = (accountId) => {
    if (!/[0-9]{12}/.exec(accountId)) {
        throw new Error("Access point ARN accountID does not match regex '[0-9]{12}'");
    }
};
const validateDNSHostLabel = (label, options = { tlsCompatible: true }) => {
    if (label.length >= 64 ||
        !/^[a-z0-9][a-z0-9.-]*[a-z0-9]$/.test(label) ||
        /(\d+\.){3}\d+/.test(label) ||
        /[.-]{2}/.test(label) ||
        (options?.tlsCompatible && DOT_PATTERN.test(label))) {
        throw new Error(`Invalid DNS label ${label}`);
    }
};
const validateCustomEndpoint = (options) => {
    if (options.isCustomEndpoint) {
        if (options.dualstackEndpoint)
            throw new Error("Dualstack endpoint is not supported with custom endpoint");
        if (options.accelerateEndpoint)
            throw new Error("Accelerate endpoint is not supported with custom endpoint");
    }
};
const getArnResources = (resource) => {
    const delimiter = resource.includes(":") ? ":" : "/";
    const [resourceType, ...rest] = resource.split(delimiter);
    if (resourceType === "accesspoint") {
        if (rest.length !== 1 || rest[0] === "") {
            throw new Error(`Access Point ARN should have one resource accesspoint${delimiter}{accesspointname}`);
        }
        return { accesspointName: rest[0] };
    }
    else if (resourceType === "outpost") {
        if (!rest[0] || rest[1] !== "accesspoint" || !rest[2] || rest.length !== 3) {
            throw new Error(`Outpost ARN should have resource outpost${delimiter}{outpostId}${delimiter}accesspoint${delimiter}{accesspointName}`);
        }
        const [outpostId, _, accesspointName] = rest;
        return { outpostId, accesspointName };
    }
    else {
        throw new Error(`ARN resource should begin with 'accesspoint${delimiter}' or 'outpost${delimiter}'`);
    }
};
const validateNoDualstack = (dualstackEndpoint) => { };
const validateNoFIPS = (useFipsEndpoint) => {
    if (useFipsEndpoint)
        throw new Error(`FIPS region is not supported with Outpost.`);
};
const validateMrapAlias = (name) => {
    try {
        name.split(".").forEach((label) => {
            validateDNSHostLabel(label);
        });
    }
    catch (e) {
        throw new Error(`"${name}" is not a DNS compatible name.`);
    }
};

const bucketHostname = (options) => {
    validateCustomEndpoint(options);
    return isBucketNameOptions(options)
        ?
            getEndpointFromBucketName(options)
        :
            getEndpointFromArn(options);
};
const getEndpointFromBucketName = ({ accelerateEndpoint = false, clientRegion: region, baseHostname, bucketName, dualstackEndpoint = false, fipsEndpoint = false, pathStyleEndpoint = false, tlsCompatible = true, isCustomEndpoint = false, }) => {
    const [clientRegion, hostnameSuffix] = isCustomEndpoint ? [region, baseHostname] : getSuffix(baseHostname);
    if (pathStyleEndpoint || !isDnsCompatibleBucketName(bucketName) || (tlsCompatible && DOT_PATTERN.test(bucketName))) {
        return {
            bucketEndpoint: false,
            hostname: dualstackEndpoint ? `s3.dualstack.${clientRegion}.${hostnameSuffix}` : baseHostname,
        };
    }
    if (accelerateEndpoint) {
        baseHostname = `s3-accelerate${dualstackEndpoint ? ".dualstack" : ""}.${hostnameSuffix}`;
    }
    else if (dualstackEndpoint) {
        baseHostname = `s3.dualstack.${clientRegion}.${hostnameSuffix}`;
    }
    return {
        bucketEndpoint: true,
        hostname: `${bucketName}.${baseHostname}`,
    };
};
const getEndpointFromArn = (options) => {
    const { isCustomEndpoint, baseHostname, clientRegion } = options;
    const hostnameSuffix = isCustomEndpoint ? baseHostname : getSuffixForArnEndpoint(baseHostname)[1];
    const { pathStyleEndpoint, accelerateEndpoint = false, fipsEndpoint = false, tlsCompatible = true, bucketName, clientPartition = "aws", } = options;
    validateArnEndpointOptions({ pathStyleEndpoint, accelerateEndpoint, tlsCompatible });
    const { service, partition, accountId, region, resource } = bucketName;
    validateService(service);
    validatePartition(partition, { clientPartition });
    validateAccountId(accountId);
    const { accesspointName, outpostId } = getArnResources(resource);
    if (service === "s3-object-lambda") {
        return getEndpointFromObjectLambdaArn({ ...options, tlsCompatible, bucketName, accesspointName, hostnameSuffix });
    }
    if (region === "") {
        return getEndpointFromMRAPArn({ ...options, mrapAlias: accesspointName, hostnameSuffix });
    }
    if (outpostId) {
        return getEndpointFromOutpostArn({ ...options, clientRegion, outpostId, accesspointName, hostnameSuffix });
    }
    return getEndpointFromAccessPointArn({ ...options, clientRegion, accesspointName, hostnameSuffix });
};
const getEndpointFromObjectLambdaArn = ({ dualstackEndpoint = false, fipsEndpoint = false, tlsCompatible = true, useArnRegion, clientRegion, clientSigningRegion = clientRegion, accesspointName, bucketName, hostnameSuffix, }) => {
    const { accountId, region, service } = bucketName;
    validateRegionalClient(clientRegion);
    const DNSHostLabel = `${accesspointName}-${accountId}`;
    validateDNSHostLabel(DNSHostLabel, { tlsCompatible });
    const endpointRegion = useArnRegion ? region : clientRegion;
    const signingRegion = useArnRegion ? region : clientSigningRegion;
    return {
        bucketEndpoint: true,
        hostname: `${DNSHostLabel}.${service}${fipsEndpoint ? "-fips" : ""}.${endpointRegion}.${hostnameSuffix}`,
        signingRegion,
        signingService: service,
    };
};
const getEndpointFromMRAPArn = ({ disableMultiregionAccessPoints, dualstackEndpoint = false, isCustomEndpoint, mrapAlias, hostnameSuffix, }) => {
    if (disableMultiregionAccessPoints === true) {
        throw new Error("SDK is attempting to use a MRAP ARN. Please enable to feature.");
    }
    validateMrapAlias(mrapAlias);
    return {
        bucketEndpoint: true,
        hostname: `${mrapAlias}${isCustomEndpoint ? "" : `.accesspoint.s3-global`}.${hostnameSuffix}`,
        signingRegion: "*",
    };
};
const getEndpointFromOutpostArn = ({ useArnRegion, clientRegion, clientSigningRegion = clientRegion, bucketName, outpostId, dualstackEndpoint = false, fipsEndpoint = false, tlsCompatible = true, accesspointName, isCustomEndpoint, hostnameSuffix, }) => {
    validateRegionalClient(clientRegion);
    const DNSHostLabel = `${accesspointName}-${bucketName.accountId}`;
    validateDNSHostLabel(DNSHostLabel, { tlsCompatible });
    const endpointRegion = useArnRegion ? bucketName.region : clientRegion;
    const signingRegion = useArnRegion ? bucketName.region : clientSigningRegion;
    validateOutpostService(bucketName.service);
    validateDNSHostLabel(outpostId, { tlsCompatible });
    validateNoFIPS(fipsEndpoint);
    const hostnamePrefix = `${DNSHostLabel}.${outpostId}`;
    return {
        bucketEndpoint: true,
        hostname: `${hostnamePrefix}${isCustomEndpoint ? "" : `.s3-outposts.${endpointRegion}`}.${hostnameSuffix}`,
        signingRegion,
        signingService: "s3-outposts",
    };
};
const getEndpointFromAccessPointArn = ({ useArnRegion, clientRegion, clientSigningRegion = clientRegion, bucketName, dualstackEndpoint = false, fipsEndpoint = false, tlsCompatible = true, accesspointName, isCustomEndpoint, hostnameSuffix, }) => {
    validateRegionalClient(clientRegion);
    const hostnamePrefix = `${accesspointName}-${bucketName.accountId}`;
    validateDNSHostLabel(hostnamePrefix, { tlsCompatible });
    const endpointRegion = useArnRegion ? bucketName.region : clientRegion;
    const signingRegion = useArnRegion ? bucketName.region : clientSigningRegion;
    validateS3Service(bucketName.service);
    return {
        bucketEndpoint: true,
        hostname: `${hostnamePrefix}${isCustomEndpoint
            ? ""
            : `.s3-accesspoint${fipsEndpoint ? "-fips" : ""}${dualstackEndpoint ? ".dualstack" : ""}.${endpointRegion}`}.${hostnameSuffix}`,
        signingRegion,
    };
};

const bucketEndpointMiddleware = (options) => (next, context) => async (args) => {
    const { Bucket: bucketName } = args.input;
    let replaceBucketInPath = options.bucketEndpoint;
    const request = args.request;
    if (protocolHttp.HttpRequest.isInstance(request)) {
        if (options.bucketEndpoint) {
            request.hostname = bucketName;
        }
        else if (utilArnParser.validate(bucketName)) {
            const bucketArn = utilArnParser.parse(bucketName);
            const clientRegion = await options.region();
            const useDualstackEndpoint = await options.useDualstackEndpoint();
            const useFipsEndpoint = await options.useFipsEndpoint();
            const { partition, signingRegion = clientRegion } = (await options.regionInfoProvider(clientRegion, { useDualstackEndpoint, useFipsEndpoint })) || {};
            const useArnRegion = await options.useArnRegion();
            const { hostname, bucketEndpoint, signingRegion: modifiedSigningRegion, signingService, } = bucketHostname({
                bucketName: bucketArn,
                baseHostname: request.hostname,
                accelerateEndpoint: options.useAccelerateEndpoint,
                dualstackEndpoint: useDualstackEndpoint,
                fipsEndpoint: useFipsEndpoint,
                pathStyleEndpoint: options.forcePathStyle,
                tlsCompatible: request.protocol === "https:",
                useArnRegion,
                clientPartition: partition,
                clientSigningRegion: signingRegion,
                clientRegion: clientRegion,
                isCustomEndpoint: options.isCustomEndpoint,
                disableMultiregionAccessPoints: await options.disableMultiregionAccessPoints(),
            });
            if (modifiedSigningRegion && modifiedSigningRegion !== signingRegion) {
                context["signing_region"] = modifiedSigningRegion;
            }
            if (signingService && signingService !== "s3") {
                context["signing_service"] = signingService;
            }
            request.hostname = hostname;
            replaceBucketInPath = bucketEndpoint;
        }
        else {
            const clientRegion = await options.region();
            const dualstackEndpoint = await options.useDualstackEndpoint();
            const fipsEndpoint = await options.useFipsEndpoint();
            const { hostname, bucketEndpoint } = bucketHostname({
                bucketName,
                clientRegion,
                baseHostname: request.hostname,
                accelerateEndpoint: options.useAccelerateEndpoint,
                dualstackEndpoint,
                fipsEndpoint,
                pathStyleEndpoint: options.forcePathStyle,
                tlsCompatible: request.protocol === "https:",
                isCustomEndpoint: options.isCustomEndpoint,
            });
            request.hostname = hostname;
            replaceBucketInPath = bucketEndpoint;
        }
        if (replaceBucketInPath) {
            request.path = request.path.replace(/^(\/)?[^\/]+/, "");
            if (request.path === "") {
                request.path = "/";
            }
        }
    }
    return next({ ...args, request });
};
const bucketEndpointMiddlewareOptions = {
    tags: ["BUCKET_ENDPOINT"],
    name: "bucketEndpointMiddleware",
    relation: "before",
    toMiddleware: "hostHeaderMiddleware",
    override: true,
};
const getBucketEndpointPlugin = (options) => ({
    applyToStack: (clientStack) => {
        clientStack.addRelativeTo(bucketEndpointMiddleware(options), bucketEndpointMiddlewareOptions);
    },
});

function resolveBucketEndpointConfig(input) {
    const { bucketEndpoint = false, forcePathStyle = false, useAccelerateEndpoint = false, useArnRegion, disableMultiregionAccessPoints = false, } = input;
    return Object.assign(input, {
        bucketEndpoint,
        forcePathStyle,
        useAccelerateEndpoint,
        useArnRegion: typeof useArnRegion === "function" ? useArnRegion : () => Promise.resolve(useArnRegion),
        disableMultiregionAccessPoints: typeof disableMultiregionAccessPoints === "function"
            ? disableMultiregionAccessPoints
            : () => Promise.resolve(disableMultiregionAccessPoints),
    });
}

exports.NODE_DISABLE_MULTIREGION_ACCESS_POINT_CONFIG_OPTIONS = NODE_DISABLE_MULTIREGION_ACCESS_POINT_CONFIG_OPTIONS;
exports.NODE_DISABLE_MULTIREGION_ACCESS_POINT_ENV_NAME = NODE_DISABLE_MULTIREGION_ACCESS_POINT_ENV_NAME;
exports.NODE_DISABLE_MULTIREGION_ACCESS_POINT_INI_NAME = NODE_DISABLE_MULTIREGION_ACCESS_POINT_INI_NAME;
exports.NODE_USE_ARN_REGION_CONFIG_OPTIONS = NODE_USE_ARN_REGION_CONFIG_OPTIONS;
exports.NODE_USE_ARN_REGION_ENV_NAME = NODE_USE_ARN_REGION_ENV_NAME;
exports.NODE_USE_ARN_REGION_INI_NAME = NODE_USE_ARN_REGION_INI_NAME;
exports.bucketEndpointMiddleware = bucketEndpointMiddleware;
exports.bucketEndpointMiddlewareOptions = bucketEndpointMiddlewareOptions;
exports.bucketHostname = bucketHostname;
exports.getArnResources = getArnResources;
exports.getBucketEndpointPlugin = getBucketEndpointPlugin;
exports.getSuffixForArnEndpoint = getSuffixForArnEndpoint;
exports.resolveBucketEndpointConfig = resolveBucketEndpointConfig;
exports.validateAccountId = validateAccountId;
exports.validateDNSHostLabel = validateDNSHostLabel;
exports.validateNoDualstack = validateNoDualstack;
exports.validateNoFIPS = validateNoFIPS;
exports.validateOutpostService = validateOutpostService;
exports.validatePartition = validatePartition;
exports.validateRegion = validateRegion;
