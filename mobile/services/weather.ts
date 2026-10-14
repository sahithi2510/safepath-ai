import { api } from './api';

export interface CurrentObservation {
  temperatureC?: number | null;
  precipitationMm?: number | null;
  windSpeedKmh?: number | null;
  relativeHumidity?: number | null;
  visibilityKm?: number | null;
  weatherCode?: number | null;
  condition?: string | null;
  icon?: string | null;
  isDay?: boolean | null;
  observedAt?: string | null;
}

export interface ForecastContext {
  precipitationProbabilityPercent?: number | null;
  forecastTimestamp?: string | null;
}

export interface WeatherUnits {
  temperature: string;
  precipitation: string;
  windSpeed: string;
  relativeHumidity: string;
  visibility: string;
  precipitationProbability: string;
}

export interface WeatherData {
  latitude: number;
  longitude: number;
  available: boolean;
  observation?: CurrentObservation | null;
  forecast?: ForecastContext | null;
  units?: WeatherUnits | null;
  source?: string | null;
  retrievedAt?: string | null;
  cached?: boolean | null;
  disclaimer?: string | null;
  message?: string | null;
}

/**
 * Maps WMO weather code to accessible emoji indicator.
 */
export const getWeatherEmoji = (code?: number | null, isDay?: boolean | null): string => {
  const day = isDay ?? true;
  if (code === null || code === undefined) return '🌤️';
  switch (code) {
    case 0:
      return day ? '☀️' : '🌙';
    case 1:
    case 2:
      return day ? '🌤️' : '☁️';
    case 3:
      return '☁️';
    case 45:
    case 48:
      return '🌫️';
    case 51:
    case 53:
    case 55:
    case 56:
    case 57:
      return '🌦️';
    case 61:
    case 63:
    case 80:
    case 81:
      return '🌧️';
    case 65:
    case 82:
      return '⛈️';
    case 71:
    case 73:
    case 75:
    case 77:
    case 85:
    case 86:
      return '❄️';
    case 95:
    case 96:
    case 99:
      return '⛈️';
    default:
      return '🌤️';
  }
};

/**
 * Fetches ambient weather context for a validated coordinate from SafePath backend.
 */
export const fetchWeather = async (lat: number, lon: number): Promise<WeatherData> => {
  const response = await api.get<WeatherData>('/weather', {
    params: { lat, lon },
  });
  return response.data;
};
