import React from 'react';
import ReactTestRenderer, {act} from 'react-test-renderer';
import {SignalInspector} from '../src/SignalInspector';

const event = {
  session_id: 'demo-session',
  event_type: 'device_intel_collection' as const,
  schema_version: 1,
  collected_at: '2026-07-15T12:00:00.000Z',
  probes: {
    device_identity: {
      status: 'success' as const,
      data: {manufacturer: 'Google', model: 'Pixel'},
      durationMs: 8,
    },
    locale: {status: 'success' as const, data: {language: 'en'}, durationMs: 2},
    network: {status: 'skipped' as const, reason: 'disabled' as const},
    telephony: {status: 'error' as const, message: 'Unavailable', durationMs: 1},
  },
};

describe('SignalInspector', () => {
  it('BUG-R1: keeps the summary contract and complete JSON payload after collection', async () => {
    const collectSignals = jest.fn().mockResolvedValue(event);
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await act(() => {
      renderer = ReactTestRenderer.create(
        <SignalInspector collectSignals={collectSignals} platformName="Android" />,
      );
    });

    const root = renderer!.root;
    expect(root.findByProps({testID: 'empty-state'}).props.children).toBeTruthy();

    await act(async () => {
      await root.findByProps({testID: 'collect-signals'}).props.onPress();
    });

    expect(collectSignals).toHaveBeenCalledTimes(1);
    expect(root.findByProps({testID: 'collection-summary'}).props.children).toBe(
      '2 observed · 1 skipped · 1 failed',
    );
    expect(root.findByProps({testID: 'raw-json'}).props.children).toContain(
      '"manufacturer": "Google"',
    );
  });

  it('shows an actionable error when native collection fails', async () => {
    const collectSignals = jest
      .fn()
      .mockRejectedValue(new Error('Native module unavailable'));
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await act(() => {
      renderer = ReactTestRenderer.create(
        <SignalInspector collectSignals={collectSignals} platformName="iOS" />,
      );
    });

    await act(async () => {
      await renderer!.root.findByProps({testID: 'collect-signals'}).props.onPress();
    });

    expect(renderer!.root.findByProps({testID: 'collection-error'}).props.children).toContain(
      'Native module unavailable',
    );
  });
});
