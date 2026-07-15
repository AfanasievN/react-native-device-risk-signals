import React, {useEffect, useMemo} from 'react';
import {Appearance, Platform, StatusBar, StyleSheet} from 'react-native';
import {DeviceIntel} from 'react-native-device-risk-signals';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';
import {SignalInspector} from './src/SignalInspector';

function App() {
  const deviceIntel = useMemo(() => new DeviceIntel(), []);

  useEffect(() => {
    Appearance.setColorScheme('light');
    return () => Appearance.setColorScheme('unspecified');
  }, []);

  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />
      <SafeAreaView edges={['top', 'right', 'bottom', 'left']} style={styles.safeArea}>
        <SignalInspector
          collectSignals={() => deviceIntel.collect()}
          platformName={Platform.OS === 'ios' ? 'iOS' : 'Android'}
        />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  safeArea: {flex: 1, backgroundColor: '#ffffff'},
});

export default App;
