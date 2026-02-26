#!/bin/bash -el
set -e

while getopts u:p:v: flag
do
    case "${flag}" in
        u) GIT_USER=${OPTARG};;
        p) GIT_TOKEN=${OPTARG};;
        v) ARTIFACT_VERSION=${OPTARG};;
        g) GITBOT_USERNAME=${OPTARG};;
    esac
done

if [ -z "$GIT_USER" ]; then
    echo 'Missing option -a (Git username)' >&2
    exit 1
fi

if [ -z "$GIT_TOKEN" ]; then
    echo 'Missing option -v (Git token)' >&2
    exit 1
fi

if [ -z "$ARTIFACT_VERSION" ]; then
    echo 'Missing option -v (Artifact version)' >&2
    exit 1
fi

if [ -z "$GITBOT_USERNAME" ]; then
    echo 'Missing option -v (Github bot username version)' >&2
    exit 1
fi

export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"

cd pulse-ui

# Read the current SNAPSHOT version and derive the published version
PUBLISHED_VERSION="${ARTIFACT_VERSION}"

# Bump patch: 0.1.0 -> 0.1.1
IFS='.' read -r MAJOR MINOR PATCH <<< "$PUBLISHED_VERSION"
NEXT_VERSION="$MAJOR.$MINOR.$((PATCH + 1))-SNAPSHOT"

echo "Published version : $PUBLISHED_VERSION"
echo "Next version      : $NEXT_VERSION"

# Update package.json in place
jq --arg v "$NEXT_VERSION" '.version = $v' package.json > package.json.tmp
mv package.json.tmp package.json

cd ..

# Configure git
git config user.name "${GITBOT_USERNAME}"
git config user.email "${GITBOT_USERNAME}@users.noreply.github.com"

git add pulse-ui/package.json
git commit -m "chore: release pulse-ui version $PUBLISHED_VERSION"

git push "https://${GIT_USER}:${GIT_TOKEN}@github.com/dream-horizon-org/pulse.git" HEAD:main