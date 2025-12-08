#!/usr/bin/env node

const { checkNodeVersion } = require('./utils');
checkNodeVersion();

const { Command } = require('commander');
const { uploadFiles } = require('./uploadService');
const TAG_OPTIONAL = '(OPTIONAL)';

const program = new Command();

program
  .name('pulse-cli')
  .description('Pulse CLI - Command-line tool for Pulse SDK')
  .usage('[command] [subcommand] [options]')
  .version(require('../package.json').version);

const uploadCommand = new Command('upload')
  .description(
    'Upload symbol files and source maps. Supports multiple file uploads.'
  )
  .usage('[subcommand] [options]');

uploadCommand
  .command('react-native-android')
  .description('Upload React Native Android files')
  .requiredOption('--api-url <url>', 'Backend API endpoint URL')
  .requiredOption(
    '--app-version <version>',
    'App version from build.gradle (e.g., 1.0.0)'
  )
  .requiredOption('--version-code <code>', 'Version code (e.g., 1)')
  .requiredOption('--js-sourcemap <path>', 'JavaScript source map file path')
  .option(
    '--bundle-id <id>',
    `${TAG_OPTIONAL} Bundle ID / Package name from build.gradle applicationId (e.g., pulsereactnativeotel.example)`
  )
  .option(
    '--java-mapping <path>',
    `${TAG_OPTIONAL} ProGuard/R8 mapping file path`
  )
  .option('--debug', `${TAG_OPTIONAL} Show debug information`)
  .action(async (options) => {
    try {
      await uploadFiles('react-native-android', options);
    } catch (error) {
      console.error(`\n✗ Error: ${error.message}`);
      process.exit(1);
    }
  });

uploadCommand
  .command('react-native-ios')
  .description('Upload React Native iOS source maps')
  .requiredOption('--api-url <url>', 'Backend API endpoint URL')
  .requiredOption(
    '--bundle-version <version>',
    'Bundle version from Info.plist CFBundleShortVersionString (e.g., 1.0.0)'
  )
  .requiredOption('--version-code <code>', 'Version code (e.g., 1)')
  .requiredOption('--js-sourcemap <path>', 'JavaScript source map file path')
  .option(
    '--bundle-id <id>',
    `${TAG_OPTIONAL} Bundle ID from Info.plist CFBundleIdentifier (e.g., pulsereactnativeotel.example)`
  )
  .option('--debug', `${TAG_OPTIONAL} Show debug information`)
  .action(async (options) => {
    try {
      await uploadFiles('react-native-ios', options);
    } catch (error) {
      console.error(`\n✗ Error: ${error.message}`);
      process.exit(1);
    }
  });

program.addCommand(uploadCommand);

program.showHelpAfterError();

program.parse();
