import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { RawSignalEvent } from 'react-native-device-risk-signals';

type SignalInspectorProps = {
  collectSignals: () => Promise<RawSignalEvent>;
  platformName?: string;
};

type ProbeCounts = {
  observed: number;
  skipped: number;
  failed: number;
};

const colors = {
  background: '#ffffff',
  surface: '#f6f6f5',
  surfaceStrong: '#ece9e7',
  ink: '#211d1c',
  muted: '#6b625f',
  border: '#dedad8',
  primary: '#c95736',
  primaryPressed: '#ad442b',
  accent: '#006d77',
  successSurface: '#e4f3f2',
  error: '#b42318',
  errorSurface: '#fff0ee',
};

function countProbes(event: RawSignalEvent): ProbeCounts {
  return Object.values(event.probes).reduce<ProbeCounts>(
    (counts, outcome) => {
      if (outcome.status === 'success') counts.observed += 1;
      if (outcome.status === 'skipped') counts.skipped += 1;
      if (outcome.status === 'error' || outcome.status === 'timeout')
        counts.failed += 1;
      return counts;
    },
    { observed: 0, skipped: 0, failed: 0 },
  );
}

function readableError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function SignalInspector({
  collectSignals,
  platformName = Platform.OS === 'ios' ? 'iOS' : 'Android',
}: SignalInspectorProps) {
  const [event, setEvent] = useState<RawSignalEvent>();
  const [error, setError] = useState<string>();
  const [collecting, setCollecting] = useState(false);
  const [collectionDurationMs, setCollectionDurationMs] = useState<number>();

  const probeCounts = useMemo(
    () => (event ? countProbes(event) : undefined),
    [event],
  );
  const json = useMemo(
    () => (event ? JSON.stringify(event, null, 2) : undefined),
    [event],
  );

  const collect = async () => {
    if (collecting) return;
    const startedAt = Date.now();
    setCollecting(true);
    setError(undefined);
    setCollectionDurationMs(undefined);
    try {
      setEvent(await collectSignals());
      setCollectionDurationMs(Date.now() - startedAt);
    } catch (collectionError) {
      setError(readableError(collectionError));
    } finally {
      setCollecting(false);
    }
  };

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <View style={styles.header}>
        <View style={styles.titleRow}>
          <Text accessibilityRole="header" style={styles.title}>
            Inspect this device
          </Text>
          <View style={styles.platformBadge}>
            <Text style={styles.platformBadgeText}>{platformName}</Text>
          </View>
        </View>
        <Text style={styles.subtitle}>
          Run every enabled probe and inspect the exact payload returned by the
          native module.
        </Text>
      </View>

      <View style={styles.privacyNote}>
        <Text style={styles.privacyTitle}>Local inspection</Text>
        <Text style={styles.privacyBody}>
          This demo calls collect() locally. Nothing is uploaded by the example
          app.
        </Text>
      </View>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Collect device signals"
        accessibilityState={{ busy: collecting, disabled: collecting }}
        disabled={collecting}
        onPress={collect}
        testID="collect-signals"
        style={({ pressed }) => [
          styles.collectButton,
          pressed && styles.collectButtonPressed,
          collecting && styles.collectButtonDisabled,
        ]}
      >
        {collecting ? <ActivityIndicator color={colors.background} /> : null}
        <Text style={styles.collectButtonText}>
          {collecting
            ? 'Collecting…'
            : event
            ? 'Collect again'
            : 'Collect device signals'}
        </Text>
      </Pressable>

      {error ? (
        <View accessibilityLiveRegion="polite" style={styles.errorPanel}>
          <Text style={styles.errorTitle}>Collection failed</Text>
          <Text testID="collection-error" style={styles.errorBody}>
            {error}. Rebuild the native app after installing or changing the
            library.
          </Text>
        </View>
      ) : null}

      {!event ? (
        <View testID="empty-state" style={styles.emptyState}>
          <Text style={styles.emptyTitle}>No collection yet</Text>
          <Text style={styles.emptyBody}>
            Results will include successful, skipped, timed-out, and failed
            probes so you can see platform differences clearly.
          </Text>
        </View>
      ) : (
        <View style={styles.results}>
          <View style={styles.resultHeadingRow}>
            <View>
              <Text accessibilityRole="header" style={styles.resultTitle}>
                Latest collection
              </Text>
              <Text style={styles.resultMeta}>
                {new Date(event.collected_at).toLocaleString()} · schema{' '}
                {event.schema_version}
              </Text>
            </View>
            <View style={styles.readyBadge}>
              <Text style={styles.readyBadgeText}>Ready</Text>
            </View>
          </View>

          <Text testID="collection-summary" style={styles.summary}>
            {`${probeCounts!.observed} observed · ${
              probeCounts!.skipped
            } skipped · ${probeCounts!.failed} failed`}
          </Text>
          {collectionDurationMs !== undefined ? (
            <Text testID="collection-duration" style={styles.benchmark}>
              {`${collectionDurationMs} ms total`}
            </Text>
          ) : null}

          <View style={styles.jsonHeader}>
            <Text style={styles.jsonTitle}>Raw JSON</Text>
            <Text style={styles.jsonHint}>Long-press to select</Text>
          </View>
          <View style={styles.jsonViewer}>
            <Text selectable testID="raw-json" style={styles.jsonText}>
              {json}
            </Text>
          </View>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: {
    width: '100%',
    maxWidth: 760,
    alignSelf: 'center',
    paddingHorizontal: 16,
    paddingTop: 24,
    paddingBottom: 48,
    gap: 20,
  },
  header: { gap: 10 },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flexWrap: 'wrap',
  },
  title: {
    color: colors.ink,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  subtitle: {
    color: colors.muted,
    fontSize: 15,
    lineHeight: 22,
    maxWidth: 620,
  },
  platformBadge: {
    minHeight: 30,
    justifyContent: 'center',
    paddingHorizontal: 11,
    borderRadius: 999,
    backgroundColor: colors.surfaceStrong,
  },
  platformBadgeText: {
    color: colors.ink,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
  },
  privacyNote: {
    backgroundColor: colors.surface,
    borderRadius: 10,
    padding: 16,
    gap: 4,
  },
  privacyTitle: {
    color: colors.ink,
    fontSize: 15,
    lineHeight: 21,
    fontWeight: '600',
  },
  privacyBody: { color: colors.muted, fontSize: 14, lineHeight: 20 },
  collectButton: {
    minHeight: 52,
    borderRadius: 10,
    backgroundColor: colors.primary,
    paddingHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  collectButtonPressed: { backgroundColor: colors.primaryPressed },
  collectButtonDisabled: { opacity: 0.72 },
  collectButtonText: {
    color: colors.background,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '700',
  },
  errorPanel: {
    backgroundColor: colors.errorSurface,
    borderRadius: 10,
    padding: 16,
    gap: 4,
  },
  errorTitle: {
    color: colors.error,
    fontSize: 15,
    lineHeight: 21,
    fontWeight: '700',
  },
  errorBody: { color: colors.ink, fontSize: 14, lineHeight: 20 },
  emptyState: {
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 14,
    paddingVertical: 28,
    paddingHorizontal: 20,
    gap: 6,
  },
  emptyTitle: {
    color: colors.ink,
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '600',
  },
  emptyBody: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21,
    maxWidth: 620,
  },
  results: { gap: 14 },
  resultHeadingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
  },
  resultTitle: {
    color: colors.ink,
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '700',
  },
  resultMeta: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 19,
    marginTop: 2,
  },
  readyBadge: {
    minHeight: 30,
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderRadius: 999,
    backgroundColor: colors.successSurface,
  },
  readyBadgeText: {
    color: colors.accent,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700',
  },
  summary: {
    color: colors.accent,
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '600',
  },
  benchmark: { color: colors.muted, fontSize: 13, lineHeight: 19 },
  jsonHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    gap: 12,
  },
  jsonTitle: {
    color: colors.ink,
    fontSize: 15,
    lineHeight: 21,
    fontWeight: '700',
  },
  jsonHint: { color: colors.muted, fontSize: 12, lineHeight: 18 },
  jsonViewer: {
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    padding: 14,
  },
  jsonText: {
    color: colors.ink,
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace' }),
    fontSize: 12,
    lineHeight: 18,
  },
});
