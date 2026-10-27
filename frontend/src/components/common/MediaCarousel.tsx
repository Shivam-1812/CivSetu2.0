import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight, Mic, FileText, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { mediaType, useMediaUrl } from "@/components/media";
import type { Media } from "@/api/types";

interface MediaCarouselProps {
  media: Media[];
  /** Describes the files for screen readers, e.g. "Photo of the issue". */
  label?: string;
}

function MediaItem({ item, label }: { item: Media; label: string }) {
  const { url, failed } = useMediaUrl(item.url);
  const type = mediaType(item);

  if (failed) {
    return <p className="text-sm text-gray-300">This file is not available to you.</p>;
  }
  if (!url) {
    return <Loader2 className="w-8 h-8 animate-spin text-white/70" />;
  }

  if (type === "voice") {
    return (
      <div className="flex flex-col items-center gap-8 text-white animate-in fade-in zoom-in-50 duration-300 w-full px-10">
        <div className="w-40 h-40 rounded-full bg-primary/20 backdrop-blur-xl border border-primary/30 flex items-center justify-center relative">
          <Mic className="w-20 h-20 text-primary" />
          <div className="absolute inset-0 rounded-full border border-primary/20 animate-ping opacity-20" />
        </div>
        <div className="transform scale-125 origin-center w-full max-w-sm flex justify-center">
          <audio
            controls
            src={url}
            aria-label="Voice note"
            className="w-full shadow-xl rounded-full accent-primary"
          />
        </div>
        <p className="text-base text-gray-300 font-medium mt-4">
          Voice Evidence
        </p>
      </div>
    );
  }

  if (type === "pdf") {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center animate-in fade-in p-4">
        <iframe
          src={url}
          className="w-full h-full rounded-lg bg-white shadow-2xl"
          title="PDF Viewer"
        />
      </div>
    );
  }

  if (type === "doc") {
    return (
      <div className="flex flex-col items-center gap-6 text-white animate-in fade-in">
        <div className="w-24 h-24 rounded-2xl bg-white/10 backdrop-blur flex items-center justify-center">
          <FileText className="w-12 h-12" />
        </div>
        <div className="text-center">
          <p className="text-lg font-semibold mb-2">Document Attachment</p>
          <Button variant="secondary" onClick={() => window.open(url, "_blank")}>
            <FileText className="w-4 h-4 mr-2" />
            View / Download Document
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="relative w-full h-full flex items-center justify-center bg-black">
      <img src={url} alt={label} className="w-full h-full object-contain" />
    </div>
  );
}

export function MediaCarousel({ media, label = "Issue proof" }: MediaCarouselProps) {
  const [currentIndex, setCurrentIndex] = useState(0);

  if (!media || media.length === 0) {
    return (
      <div className="w-full h-full min-h-[200px] flex items-center justify-center bg-black/90 text-white">
        <p className="text-sm text-gray-400">No media attachments</p>
      </div>
    );
  }

  const index = Math.min(currentIndex, media.length - 1);

  const handleNext = () => {
    setCurrentIndex((index + 1) % media.length);
  };

  const handlePrev = () => {
    setCurrentIndex(index === 0 ? media.length - 1 : index - 1);
  };

  return (
    <div className="w-full h-full min-h-[240px] bg-black/95 relative flex items-center justify-center overflow-hidden">
      {/* Navigation Arrows */}
      {media.length > 1 && (
        <>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Previous file"
            className="absolute left-4 text-white hover:bg-white/10 z-10"
            onClick={handlePrev}
          >
            <ChevronLeft className="w-8 h-8" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Next file"
            className="absolute right-4 text-white hover:bg-white/10 z-10"
            onClick={handleNext}
          >
            <ChevronRight className="w-8 h-8" />
          </Button>
        </>
      )}

      {/* Media Content */}
      <div className="w-full h-full flex items-center justify-center bg-black/40">
        <MediaItem key={media[index].id} item={media[index]} label={label} />
      </div>

      {/* Dots Indicator */}
      {media.length > 1 && (
        <div className="absolute bottom-4 flex gap-2 z-10">
          {media.map((item, idx) => (
            <div
              key={item.id}
              className={cn(
                "w-2 h-2 rounded-full transition-all",
                idx === index ? "bg-white w-4" : "bg-white/50",
              )}
            />
          ))}
        </div>
      )}
    </div>
  );
}
