const { withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

/**
 * expo-example-rn076 only: adds `mavenLocal()` to the generated Android root `build.gradle`
 * so Gradle can resolve pulse-android-otel artifacts published locally by CI's `m2-publish` job.
 * Not part of the published npm package.
 */
const withMavenLocal = (config) => {
  config = withDangerousMod(config, [
    'android',
    async (modConfig) => {
      const buildGradlePath = path.join(
        modConfig.modRequest.platformProjectRoot,
        'build.gradle'
      );

      if (!fs.existsSync(buildGradlePath)) {
        console.warn(`build.gradle not found at ${buildGradlePath}`);
        return modConfig;
      }

      try {
        let contents = fs.readFileSync(buildGradlePath, 'utf8');

        if (contents.includes('buildscript')) {
          if (
            !contents.match(
              /buildscript\s*\{[\s\S]*?repositories\s*\{[\s\S]*?mavenLocal\(\)/
            )
          ) {
            contents = contents.replace(
              /(buildscript\s*\{[\s\S]*?repositories\s*\{)/,
              (match) =>
                match.includes('mavenLocal()')
                  ? match
                  : match + '\n    mavenLocal()'
            );
          }
        }

        if (contents.includes('allprojects')) {
          if (
            !contents.match(
              /allprojects\s*\{[\s\S]*?repositories\s*\{[\s\S]*?mavenLocal\(\)/
            )
          ) {
            contents = contents.replace(
              /(allprojects\s*\{[\s\S]*?repositories\s*\{)/,
              (match) =>
                match.includes('mavenLocal()')
                  ? match
                  : match + '\n    mavenLocal()'
            );
          }
        }

        fs.writeFileSync(buildGradlePath, contents);
      } catch (error) {
        console.error('Error modifying build.gradle:', error);
      }

      return modConfig;
    },
  ]);

  return config;
};

module.exports = withMavenLocal;
