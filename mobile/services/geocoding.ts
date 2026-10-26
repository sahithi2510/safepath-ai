/**
 * SafePath AI — Geocoding & Place Search Service
 * 
 * Powered by OpenStreetMap Nominatim.
 * Free, open-source, zero API keys required.
 */

export interface GeocodedPlace {
  id: string;
  name: string;
  displayName: string;
  latitude: number;
  longitude: number;
  type?: string;
}

const NOMINATIM_BASE_URL = 'https://nominatim.openstreetmap.org';

/**
 * Search places and addresses matching a human-readable text query.
 */
export async function searchPlaces(
  query: string,
  userLat?: number,
  userLon?: number
): Promise<GeocodedPlace[]> {
  if (!query || query.trim().length < 2) {
    return [];
  }

  try {
    let url = `${NOMINATIM_BASE_URL}/search?format=json&q=${encodeURIComponent(query.trim())}&limit=6&addressdetails=1`;
    
    // If user's approximate coordinates are available, bias results towards user's region
    if (userLat != null && userLon != null) {
      const delta = 0.5; // roughly 50km
      const left = userLon - delta;
      const right = userLon + delta;
      const top = userLat + delta;
      const bottom = userLat - delta;
      url += `&viewbox=${left},${top},${right},${bottom}`;
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'SafePath-AI/1.0 (Academic Project)',
        'Accept': 'application/json',
      },
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      return [];
    }

    const data = await response.json();
    if (!Array.isArray(data)) {
      return [];
    }

    return data.map((item: any, index: number) => {
      const parts = (item.display_name || '').split(',');
      const shortName = item.name || parts[0]?.trim() || query;
      const subTitle = parts.slice(1, 4).map((p: string) => p.trim()).join(', ');

      return {
        id: item.place_id ? String(item.place_id) : `place_${index}_${Date.now()}`,
        name: shortName,
        displayName: subTitle ? `${shortName}, ${subTitle}` : shortName,
        latitude: parseFloat(item.lat),
        longitude: parseFloat(item.lon),
        type: item.type || item.class || 'place',
      };
    });
  } catch (error) {
    console.warn('Place search failed (offline or network error):', error);
    return [];
  }
}

/**
 * Reverse geocodes latitude/longitude into a human-readable address.
 */
export async function reverseGeocode(lat: number, lon: number): Promise<string> {
  try {
    const url = `${NOMINATIM_BASE_URL}/reverse?format=json&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1`;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'SafePath-AI/1.0 (Academic Project)',
        'Accept': 'application/json',
      },
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      return `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
    }

    const data = await response.json();
    if (data && data.address) {
      const addr = data.address;
      const road = addr.road || addr.pedestrian || addr.suburb || addr.neighbourhood || addr.residential;
      const city = addr.city || addr.town || addr.village || addr.county || addr.state_district;
      if (road && city) {
        return `${road}, ${city}`;
      } else if (road) {
        return road;
      } else if (city) {
        return city;
      }
    }

    if (data && data.display_name) {
      const parts = data.display_name.split(',');
      return parts.slice(0, 3).map((p: string) => p.trim()).join(', ');
    }

    return `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
  } catch (error) {
    return `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
  }
}
