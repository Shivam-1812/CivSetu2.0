import { Fragment, useMemo, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { divIcon } from "leaflet";
import { Circle, CircleMarker, MapContainer, Marker, Popup } from "react-leaflet";
import { ChevronRight } from "lucide-react";
import { issueApi, referenceApi, type IssueFilters } from "@/api/endpoints";
import type { IssueCluster } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { categoryIcon, priorityColors, priorityConfig, statusConfig } from "@/lib/issues";
import { DEFAULT_CENTER, FlyTo, Tiles, ViewportWatcher } from "@/components/maps";

interface CivicMapProps {
  /** Same filters as the issue list; markers load for the visible area. */
  filters?: IssueFilters;
  onIssueSelect: (issueId: string) => void;
  /** Fly here, e.g. after "View on Map" in the issue dialog. */
  focus?: [number, number] | null;
  clusters?: IssueCluster[];
  clusterRadius?: number;
  onClusterSelect?: (cluster: IssueCluster) => void;
  className?: string;
}

const clusterIcon = (count: number) =>
  divIcon({
    className: "",
    html: `<div style="color:white;font-weight:700;font-size:14px;text-shadow:0 1px 2px rgba(0,0,0,.5);transform:translate(-50%,-50%)">${count}</div>`,
  });

/** The city issue map (Leaflet + OpenStreetMap), with optional survey clusters drawn as circles. */
export function CivicMap({
  filters = {},
  onIssueSelect,
  focus,
  clusters = [],
  clusterRadius = 500,
  onClusterSelect,
  className,
}: CivicMapProps) {
  const [bbox, setBbox] = useState<string>();
  const markers = useQuery({
    queryKey: ["issue-map", filters, bbox],
    queryFn: () => issueApi.map({ ...filters, bbox: bbox! }),
    enabled: !!bbox,
    placeholderData: keepPreviousData,
  });
  const categories = useQuery({ queryKey: ["categories"], queryFn: referenceApi.categories, staleTime: 5 * 60_000 });
  const labels = useMemo(
    () => new Map(categories.data?.map((category) => [category.code, category.label])),
    [categories.data],
  );

  return (
    <div className="flex justify-center w-full">
      <div
        className={cn(
          "relative w-full lg:w-[90%] h-[500px] rounded-2xl overflow-hidden shadow-xl border border-border/50",
          className,
        )}
      >
        <MapContainer center={DEFAULT_CENTER} zoom={12} className="w-full h-full" scrollWheelZoom>
          <Tiles />
          <ViewportWatcher onChange={setBbox} />
          <FlyTo position={focus ?? null} />

          {/* Survey clusters */}
          {clusters.map((cluster) => {
            const center: [number, number] = [cluster.centerLatitude!, cluster.centerLongitude!];
            return (
              <Fragment key={center.join(",")}>
                <Circle
                  center={center}
                  radius={clusterRadius}
                  pathOptions={{ color: "#EA580C", weight: 2, fillColor: "#F97316", fillOpacity: 0.35 }}
                  eventHandlers={{ click: () => onClusterSelect?.(cluster) }}
                />
                <Marker
                  position={center}
                  icon={clusterIcon(cluster.issueCount ?? 0)}
                  eventHandlers={{ click: () => onClusterSelect?.(cluster) }}
                />
              </Fragment>
            );
          })}

          {markers.data?.items?.map((point) => (
            <CircleMarker
              key={point.id}
              center={[point.latitude!, point.longitude!]}
              radius={8}
              pathOptions={{
                color: "#ffffff",
                weight: 2,
                fillColor: priorityColors[point.priority ?? "LOW"],
                fillOpacity: 0.9,
              }}
            >
              <Popup>
                <div className="p-1 max-w-[200px] text-black">
                  <p className="font-bold text-sm mb-1">
                    {categoryIcon(point.categoryCode)} {labels.get(point.categoryCode) ?? point.categoryCode}
                  </p>
                  <div className="flex flex-wrap items-center gap-1.5 mb-2">
                    <Badge className={cn("text-[10px] h-5 px-1.5", statusConfig[point.status ?? "REPORTED"].class)}>
                      {statusConfig[point.status ?? "REPORTED"].label}
                    </Badge>
                    <Badge className={cn("text-[10px] h-5 px-1.5", priorityConfig[point.priority ?? "MEDIUM"].class)}>
                      {priorityConfig[point.priority ?? "MEDIUM"].label}
                    </Badge>
                  </div>
                  <Button size="sm" className="w-full h-7 text-xs" onClick={() => onIssueSelect(point.id!)}>
                    View Details <ChevronRight className="w-3 h-3 ml-1" />
                  </Button>
                </div>
              </Popup>
            </CircleMarker>
          ))}
        </MapContainer>
        {markers.data?.truncated && (
          <p className="absolute top-3 left-1/2 z-[1000] -translate-x-1/2 rounded-full bg-card/95 px-3 py-1 text-xs shadow">
            Showing the newest 1,000 issues here. Zoom in to see all.
          </p>
        )}
      </div>
    </div>
  );
}
