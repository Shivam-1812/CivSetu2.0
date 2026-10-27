import type { LatLngBounds, LeafletMouseEvent } from 'leaflet';
import { useEffect, useState } from 'react';
import { CircleMarker, MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';

import { Button } from './shared';

/** Default view: Pune. */
export const DEFAULT_CENTER: [number, number] = [18.5204, 73.8567];

// OpenStreetMap's public tiles are fine for development and light use; check the provider's
// usage policy (or switch to a commercial tile service) before heavy production traffic.
export function Tiles() {
  return (
    <TileLayer
      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    />
  );
}

/** The visible area in the API's bbox format: minLng,minLat,maxLng,maxLat. */
export function toBbox(bounds: LatLngBounds): string {
  const clamp = (value: number, limit: number) => Math.max(-limit, Math.min(limit, value));
  return [
    clamp(bounds.getWest(), 180),
    clamp(bounds.getSouth(), 90),
    clamp(bounds.getEast(), 180),
    clamp(bounds.getNorth(), 90),
  ]
    .map((value) => value.toFixed(5))
    .join(',');
}

export function ViewportWatcher({ onChange }: { onChange: (bbox: string) => void }) {
  const map = useMapEvents({ moveend: () => onChange(toBbox(map.getBounds())) });
  // Report the initial viewport once
  useEffect(() => {
    onChange(toBbox(map.getBounds()));
  }, [map, onChange]);
  return null;
}

function ClickToPick({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (event: LeafletMouseEvent) => onPick(event.latlng.lat, event.latlng.lng) });
  return null;
}

export function FlyTo({ position }: { position: [number, number] | null }) {
  const map = useMap();
  useEffect(() => {
    if (position) map.flyTo(position, Math.max(map.getZoom(), 16));
  }, [map, position]);
  return null;
}

/** Choose a location by tapping the map or using the device's GPS. */
export function LocationPicker({
  value,
  onChange,
}: {
  value: [number, number] | null;
  onChange: (position: [number, number]) => void;
}) {
  const [locating, setLocating] = useState(false);
  const [gpsError, setGpsError] = useState<string>();
  const [flyTarget, setFlyTarget] = useState<[number, number] | null>(null);

  const useMyLocation = () => {
    if (!navigator.geolocation) {
      setGpsError('This browser cannot share your location. Tap the map instead.');
      return;
    }
    setLocating(true);
    setGpsError(undefined);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const point: [number, number] = [position.coords.latitude, position.coords.longitude];
        onChange(point);
        setFlyTarget(point);
        setLocating(false);
      },
      () => {
        setGpsError('Could not get your location. Allow location access, or tap the map instead.');
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  return (
    <div className="space-y-2">
      <div className="h-72 overflow-hidden rounded-xl border border-slate-200">
        <MapContainer center={value ?? DEFAULT_CENTER} zoom={value ? 16 : 12} className="h-full w-full">
          <Tiles />
          <ClickToPick onPick={(lat, lng) => onChange([lat, lng])} />
          <FlyTo position={flyTarget} />
          {value && (
            <CircleMarker
              center={value}
              radius={10}
              pathOptions={{ color: '#ffffff', weight: 3, fillColor: '#0f766e', fillOpacity: 1 }}
            />
          )}
        </MapContainer>
      </div>
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <Button variant="secondary" busy={locating} onClick={useMyLocation}>
          Use my location
        </Button>
        <span className="text-slate-600">
          {value ? `Selected: ${value[0].toFixed(5)}, ${value[1].toFixed(5)}` : 'Tap the map where the problem is.'}
        </span>
      </div>
      {gpsError && <p className="text-sm text-red-600">{gpsError}</p>}
    </div>
  );
}
