const { withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

/**
 * expo-example only: adds `mavenLocal()` to the generated Android root `build.gradle`
 * so Gradle can resolve Pulse Android artifacts you published locally. Not part of
 * the published npm package. Remove this plugin from `app.json` to use only
 * Maven Central (hosted) dependencies from `pulse-react-native-otel/android/build.gradle`.
 */
const withMavenLocal = (config) => {
  // Add mavenLocal() to both buildscript and allprojects repositories in root build.gradle
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
        let buildGradleContents = fs.readFileSync(buildGradlePath, 'utf8');

        // Add mavenLocal() to buildscript repositories block
        if (buildGradleContents.includes('buildscript')) {
          // Check if mavenLocal() already exists in buildscript repositories
          if (
            !buildGradleContents.match(
              /buildscript\s*\{[\s\S]*?repositories\s*\{[\s\S]*?mavenLocal\(\)/
            )
          ) {
            // Find repositories { inside buildscript and add mavenLocal() after it
            buildGradleContents = buildGradleContents.replace(
              /(buildscript\s*\{[\s\S]*?repositories\s*\{)/,
              (match) => {
                // Check if mavenLocal() is already in this block
                if (match.includes('mavenLocal()')) {
                  return match;
                }
                return match + '\n    mavenLocal()';
              }
            );
          }
        }

        // Add mavenLocal() to allprojects repositories block
        if (buildGradleContents.includes('allprojects')) {
          // Check if mavenLocal() already exists in allprojects repositories
          if (
            !buildGradleContents.match(
              /allprojects\s*\{[\s\S]*?repositories\s*\{[\s\S]*?mavenLocal\(\)/
            )
          ) {
            // Find repositories { inside allprojects and add mavenLocal() after it
            buildGradleContents = buildGradleContents.replace(
              /(allprojects\s*\{[\s\S]*?repositories\s*\{)/,
              (match) => {
                // Check if mavenLocal() is already in this block
                if (match.includes('mavenLocal()')) {
                  return match;
                }
                return match + '\n    mavenLocal()';
              }
            );
          }
        }

        fs.writeFileSync(buildGradlePath, buildGradleContents);
      } catch (error) {
        console.error('Error modifying build.gradle:', error);
      }

      return modConfig;
    },
  ]);

  return config;
};

module.exports = withMavenLocal;
