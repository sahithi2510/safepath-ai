const { withAndroidManifest } = require('@expo/config-plugins');

module.exports = function withCleartextTraffic(config) {
  return withAndroidManifest(config, (config) => {
    if (config.modResults.manifest.application && config.modResults.manifest.application[0]) {
      config.modResults.manifest.application[0].$['android:usesCleartextTraffic'] = 'true';
    }
    return config;
  });
};
