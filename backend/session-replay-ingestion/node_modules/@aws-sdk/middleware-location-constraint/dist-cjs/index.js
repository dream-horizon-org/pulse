'use strict';

function locationConstraintMiddleware(options) {
    return (next) => async (args) => {
        const { CreateBucketConfiguration } = args.input;
        const region = await options.region();
        if (!CreateBucketConfiguration?.LocationConstraint && !CreateBucketConfiguration?.Location) {
            if (region !== "us-east-1") {
                args.input.CreateBucketConfiguration = args.input.CreateBucketConfiguration ?? {};
                args.input.CreateBucketConfiguration.LocationConstraint = region;
            }
        }
        return next(args);
    };
}
const locationConstraintMiddlewareOptions = {
    step: "initialize",
    tags: ["LOCATION_CONSTRAINT", "CREATE_BUCKET_CONFIGURATION"],
    name: "locationConstraintMiddleware",
    override: true,
};
const getLocationConstraintPlugin = (config) => ({
    applyToStack: (clientStack) => {
        clientStack.add(locationConstraintMiddleware(config), locationConstraintMiddlewareOptions);
    },
});

exports.getLocationConstraintPlugin = getLocationConstraintPlugin;
exports.locationConstraintMiddleware = locationConstraintMiddleware;
exports.locationConstraintMiddlewareOptions = locationConstraintMiddlewareOptions;
