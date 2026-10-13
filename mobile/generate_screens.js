const fs = require('fs');
const path = require('path');

const screens = [
  'app/(auth)/_layout.tsx',
  'app/(auth)/login.tsx',
  'app/(auth)/register.tsx',
  'app/(tabs)/_layout.tsx',
  'app/(tabs)/index.tsx', // Map/Home
  'app/(tabs)/routes.tsx',
  'app/(tabs)/reports.tsx',
  'app/(tabs)/profile.tsx',
  'app/route/[id].tsx', // Route Details
  'app/navigation.tsx', // Active navigation
  'app/add-report.tsx',
  'app/report/[id].tsx', // Report details
  'app/emergency.tsx',
  'app/safe-zones.tsx',
  'app/settings.tsx',
  'app/edit-profile.tsx',
  'app/terms.tsx'
];

screens.forEach(screen => {
  const fullPath = path.join(__dirname, screen);
  const dir = path.dirname(fullPath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  
  const componentName = path.basename(screen, '.tsx').replace(/\[|\]/g, '').replace(/(^\w|-\w)/g, (m) => m.replace('-', '').toUpperCase()) + 'Screen';
  
  let content = '';
  if (screen.endsWith('_layout.tsx')) {
    if (screen.includes('(tabs)')) {
      content = `import { Tabs } from 'expo-router';

export default function TabLayout() {
  return (
    <Tabs screenOptions={{ headerShown: false }}>
      <Tabs.Screen name="index" options={{ title: 'Map' }} />
      <Tabs.Screen name="routes" options={{ title: 'Routes' }} />
      <Tabs.Screen name="reports" options={{ title: 'Reports' }} />
      <Tabs.Screen name="profile" options={{ title: 'Profile' }} />
    </Tabs>
  );
}
`;
    } else {
      content = `import { Stack } from 'expo-router';

export default function AuthLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
`;
    }
  } else {
    content = `import { View, Text, StyleSheet } from 'react-native';

export default function ${componentName === 'IndexScreen' ? 'Screen' : componentName}() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>${screen}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
  }
});
`;
  }
  
  fs.writeFileSync(fullPath, content);
});

console.log('Created ' + screens.length + ' screens.');
