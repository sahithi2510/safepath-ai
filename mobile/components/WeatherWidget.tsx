import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { fetchWeather, WeatherData, getWeatherEmoji } from '../services/weather';

interface WeatherWidgetProps {
  latitude: number;
  longitude: number;
  compact?: boolean;
  style?: any;
}

export const WeatherWidget: React.FC<WeatherWidgetProps> = ({
  latitude,
  longitude,
  compact = true,
  style,
}) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [weather, setWeather] = useState<WeatherData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<boolean>(!compact);

  // Quantize coordinates to avoid re-fetching on micro-movements
  const roundedLat = Math.round(latitude * 100) / 100;
  const roundedLon = Math.round(longitude * 100) / 100;

  const loadWeather = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchWeather(roundedLat, roundedLon);
      setWeather(data);
    } catch (err: any) {
      setError('Weather unavailable');
    } finally {
      setLoading(false);
    }
  }, [roundedLat, roundedLon]);

  useEffect(() => {
    loadWeather();
  }, [loadWeather]);

  const obs = weather?.observation;
  const forecast = weather?.forecast;
  const emoji = getWeatherEmoji(obs?.weatherCode, obs?.isDay);

  if (compact && !expanded) {
    return (
      <TouchableOpacity
        style={[styles.compactPill, style]}
        onPress={() => setExpanded(true)}
        accessibilityRole="button"
        accessibilityLabel="Open weather details"
      >
        {loading ? (
          <ActivityIndicator size="small" color="#1F2937" style={styles.spinner} />
        ) : error || !weather?.available ? (
          <Text style={styles.compactText}>☁️ Weather N/A</Text>
        ) : (
          <Text style={styles.compactText}>
            {emoji} {obs?.temperatureC != null ? `${Math.round(obs.temperatureC)}°C` : '--'}
            {obs?.precipitationMm != null && obs.precipitationMm > 0
              ? ` • ${obs.precipitationMm}mm`
              : ''}
          </Text>
        )}
      </TouchableOpacity>
    );
  }

  return (
    <View style={[styles.cardContainer, style]} accessibilityRole="summary" accessibilityLabel="Ambient Weather Context">
      <View style={styles.headerRow}>
        <View style={styles.titleWithIcon}>
          <Text style={styles.titleIcon}>{emoji}</Text>
          <View>
            <Text style={styles.cardTitle}>Ambient Weather Context</Text>
            <Text style={styles.subtitle}>Environmental observation (not a crime metric)</Text>
          </View>
        </View>
        {compact && (
          <TouchableOpacity
            onPress={() => setExpanded(false)}
            style={styles.closeBtn}
            accessibilityRole="button"
            accessibilityLabel="Collapse weather widget"
          >
            <Text style={styles.closeBtnText}>✕</Text>
          </TouchableOpacity>
        )}
      </View>

      {loading ? (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="small" color="#2563EB" />
          <Text style={styles.loadingText}>Loading weather context...</Text>
        </View>
      ) : error || !weather?.available ? (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>
            {weather?.message || 'Weather context currently unavailable from provider.'}
          </Text>
          <TouchableOpacity
            style={styles.retryBtn}
            onPress={loadWeather}
            accessibilityRole="button"
            accessibilityLabel="Retry fetching weather"
          >
            <Text style={styles.retryBtnText}>↻ Retry</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={styles.contentBody}>
          {/* Main Temperature & Condition Display */}
          <View style={styles.mainObservationRow}>
            <Text style={styles.largeTemp}>
              {obs?.temperatureC != null ? `${Math.round(obs.temperatureC)}°C` : '--'}
            </Text>
            <View style={styles.conditionCol}>
              <Text style={styles.conditionText}>{obs?.condition || 'Observed Conditions'}</Text>
              <Text style={styles.observedTimeText}>
                {obs?.observedAt ? `Observed: ${obs.observedAt}` : 'Recent observation'}
              </Text>
            </View>
          </View>

          {/* Metric Grid: Current Observations */}
          <View style={styles.metricsGrid}>
            <View style={styles.metricItem}>
              <Text style={styles.metricLabel}>Precipitation</Text>
              <Text style={styles.metricValue}>
                {obs?.precipitationMm != null ? `${obs.precipitationMm} mm` : '0 mm'}
              </Text>
            </View>
            <View style={styles.metricItem}>
              <Text style={styles.metricLabel}>Wind Speed</Text>
              <Text style={styles.metricValue}>
                {obs?.windSpeedKmh != null ? `${obs.windSpeedKmh} km/h` : '--'}
              </Text>
            </View>
            <View style={styles.metricItem}>
              <Text style={styles.metricLabel}>Humidity</Text>
              <Text style={styles.metricValue}>
                {obs?.relativeHumidity != null ? `${obs.relativeHumidity}%` : '--'}
              </Text>
            </View>
            <View style={styles.metricItem}>
              <Text style={styles.metricLabel}>Visibility</Text>
              <Text style={styles.metricValue}>
                {obs?.visibilityKm != null ? `${obs.visibilityKm} km` : '--'}
              </Text>
            </View>
          </View>

          {/* Forecast Context (Clearly Distinguished from Observations) */}
          {forecast?.precipitationProbabilityPercent != null && (
            <View style={styles.forecastBox}>
              <Text style={styles.forecastBadge}>FORECAST</Text>
              <Text style={styles.forecastText}>
                Precipitation probability in next hour:{' '}
                <Text style={styles.forecastBold}>
                  {forecast.precipitationProbabilityPercent}%
                </Text>
              </Text>
            </View>
          )}

          {/* Provenance & Disclaimer Footer */}
          <View style={styles.footerRow}>
            <Text style={styles.sourceText}>
              Source: {weather.source || 'Open-Meteo'}
              {weather.cached ? ' • Cached' : ' • Live'}
            </Text>
          </View>
          <Text style={styles.disclaimerText}>
            {weather.disclaimer ||
              'Weather context represents ambient environmental conditions and does not measure personal safety directly.'}
          </Text>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  compactPill: {
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
    flexDirection: 'row',
    alignItems: 'center',
  },
  compactText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1F2937',
  },
  spinner: {
    marginRight: 4,
  },
  cardContainer: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 3,
    marginVertical: 6,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  titleWithIcon: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  titleIcon: {
    fontSize: 22,
    marginRight: 8,
  },
  cardTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#111827',
  },
  subtitle: {
    fontSize: 11,
    color: '#6B7280',
  },
  closeBtn: {
    padding: 4,
  },
  closeBtnText: {
    fontSize: 14,
    color: '#9CA3AF',
    fontWeight: '700',
  },
  loadingBox: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
  },
  loadingText: {
    marginLeft: 8,
    fontSize: 12,
    color: '#4B5563',
  },
  errorBox: {
    paddingVertical: 8,
    alignItems: 'center',
  },
  errorText: {
    fontSize: 12,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 6,
  },
  retryBtn: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    backgroundColor: '#F3F4F6',
    borderRadius: 6,
  },
  retryBtnText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2563EB',
  },
  contentBody: {
    marginTop: 4,
  },
  mainObservationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  largeTemp: {
    fontSize: 28,
    fontWeight: '800',
    color: '#111827',
    marginRight: 12,
  },
  conditionCol: {
    flex: 1,
  },
  conditionText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
  },
  observedTimeText: {
    fontSize: 11,
    color: '#9CA3AF',
  },
  metricsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    backgroundColor: '#F9FAFB',
    borderRadius: 8,
    padding: 8,
    marginBottom: 8,
  },
  metricItem: {
    width: '50%',
    paddingVertical: 4,
    paddingHorizontal: 6,
  },
  metricLabel: {
    fontSize: 10,
    color: '#6B7280',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  metricValue: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1F2937',
  },
  forecastBox: {
    backgroundColor: '#EFF6FF',
    borderRadius: 6,
    padding: 8,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  forecastBadge: {
    fontSize: 9,
    fontWeight: '700',
    color: '#1D4ED8',
    backgroundColor: '#DBEAFE',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginRight: 8,
    letterSpacing: 0.5,
  },
  forecastText: {
    fontSize: 12,
    color: '#1E40AF',
    flex: 1,
  },
  forecastBold: {
    fontWeight: '700',
  },
  footerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  sourceText: {
    fontSize: 10,
    color: '#9CA3AF',
  },
  disclaimerText: {
    fontSize: 10,
    color: '#9CA3AF',
    fontStyle: 'italic',
    lineHeight: 14,
  },
});
