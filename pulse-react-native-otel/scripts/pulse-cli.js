#!/usr/bin/env node

const { checkAndAssertNodeVersion } = require('./utils');
checkAndAssertNodeVersion();

const { Command } = require('commander');
const { upload } = require('./uploadService');
const TAG_OPTIONAL = '(OPTIONAL)';

const program = new Command();

program
  .name('pulse-cli')
  .description('Pulse CLI - Command-line tool for Pulse SDK')
  .usage('[command] [subcommand] [options]')
  .version(require('../package.json').version);

const uploadCommand = new Command('upload')
  .description(
    'Upload multiple symbol files to deobfuscate stack traces and improve error debugging.'
  )
  .usage('[subcommand] [options]');

uploadCommand
  .command('react-native-android')
  .description('Upload React Native Android files')
  .requiredOption(
    '-u, --api-url <url>',
    'Backend API URL for uploading source maps and related build artifacts.'
  )
  .requiredOption(
    '-v, --app-version <version>',
    'App version of the application (e.g., 1.0.0)'
  )
  .requiredOption('-c, --version-code <code>', 'Version code (e.g., 1)')
  .requiredOption(
    '-j, --js-sourcemap <path>',
    'JavaScript source map file path'
  )
  .option(
    '-b, --bundle-id <id>',
    `${TAG_OPTIONAL} CodePush bundle label for identifying the specific bundle version (e.g., v1)`
  )
  .option(
    '-m, --mapping <path>',
    `${TAG_OPTIONAL} ProGuard/R8 mapping file path`
  )
  .option('-d, --debug', `${TAG_OPTIONAL} Show debug information`)
  .action(async (options) => {
    await upload('react-native-android', options);
  });

uploadCommand
  .command('react-native-ios')
  .description('Upload React Native iOS source maps')
  .requiredOption(
    '-u, --api-url <url>',
    'Backend API URL for uploading source maps and related build artifacts.'
  )
  .requiredOption(
    '-v, --bundle-version <version>',
    'Bundle version from Info.plist CFBundleShortVersionString (e.g., 1.0.0)'
  )
  .requiredOption('-c, --version-code <code>', 'Version code (e.g., 1)')
  .requiredOption(
    '-j, --js-sourcemap <path>',
    'JavaScript source map file path'
  )
  .option(
    '-b, --bundle-id <id>',
    `${TAG_OPTIONAL} CodePush bundle label for identifying the specific bundle version (e.g., v1)`
  )
  .option('-d, --debug', `${TAG_OPTIONAL} Show debug information`)
  .action(async (options) => {
    await upload('react-native-ios', options);
  });

program.addCommand(uploadCommand);

program.showHelpAfterError();

program.parse();
