import { useState, useRef, useEffect } from "react";
import { Mic, Square } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { Textarea } from "@/components/ui/textarea";

interface VoiceRecorderProps {
  onRecordingComplete: (audioBlob: Blob) => void;
  onTranscriptUpdate?: (transcript: string) => void;
}

/* The Web Speech API is not in TypeScript's DOM types; only what is used here is declared. */
interface SpeechResultEvent {
  resultIndex: number;
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>;
}
interface SpeechRecognizer {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((event: SpeechResultEvent) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  start: () => void;
  stop: () => void;
}
type SpeechRecognizerClass = new () => SpeechRecognizer;

const MAX_SECONDS = 120;

/**
 * Records a voice note and, where the browser supports it, transcribes the speech live with the
 * browser's own speech recognition (nothing is sent to a third-party speech service by this app).
 */
export const VoiceRecorder = ({
  onRecordingComplete,
  onTranscriptUpdate,
}: VoiceRecorderProps) => {
  const { toast } = useToast();
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [transcript, setTranscript] = useState("");
  const [interimTranscript, setInterimTranscript] = useState("");

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const recognitionRef = useRef<SpeechRecognizer | null>(null);
  const transcriptRef = useRef("");
  // Latest callbacks, so the recogniser (created once) never calls a stale one
  const callbacks = useRef({ onRecordingComplete, onTranscriptUpdate });
  useEffect(() => {
    callbacks.current = { onRecordingComplete, onTranscriptUpdate };
  });

  useEffect(() => {
    const speech = window as unknown as {
      SpeechRecognition?: SpeechRecognizerClass;
      webkitSpeechRecognition?: SpeechRecognizerClass;
    };
    const Recognizer = speech.SpeechRecognition ?? speech.webkitSpeechRecognition;
    if (!Recognizer) return;

    const recognition = new Recognizer();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = "en-IN";

    recognition.onresult = (event) => {
      let interimText = "";
      let finalText = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const piece = event.results[i][0].transcript;
        if (event.results[i].isFinal) finalText += piece + " ";
        else interimText += piece;
      }
      if (finalText) {
        transcriptRef.current += finalText;
        setTranscript(transcriptRef.current);
        callbacks.current.onTranscriptUpdate?.(transcriptRef.current.trim());
      }
      setInterimTranscript(interimText);
    };
    recognition.onerror = () => {
      // Transcription is a convenience; the recording itself continues
    };
    recognitionRef.current = recognition;

    return () => recognition.stop();
  }, []);

  useEffect(
    () => () => {
      if (timerRef.current) clearInterval(timerRef.current);
      mediaRecorderRef.current?.stream.getTracks().forEach((track) => track.stop());
    },
    [],
  );

  const stopRecording = () => {
    const recorder = mediaRecorderRef.current;
    if (!recorder || recorder.state === "inactive") return;
    recorder.stop();
    setIsRecording(false);
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
    recognitionRef.current?.stop();
  };

  // Voice notes are capped at two minutes
  useEffect(() => {
    if (isRecording && recordingTime >= MAX_SECONDS) stopRecording();
  });

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      chunksRef.current = [];

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };

      mediaRecorder.onstop = () => {
        const audioBlob = new Blob(chunksRef.current, { type: mediaRecorder.mimeType || "audio/webm" });
        callbacks.current.onRecordingComplete(audioBlob);
        stream.getTracks().forEach((track) => track.stop());
      };

      mediaRecorder.start();
      setIsRecording(true);
      setRecordingTime(0);
      transcriptRef.current = "";
      setTranscript("");
      setInterimTranscript("");

      try {
        recognitionRef.current?.start();
      } catch {
        // Already running in this browser session
      }

      timerRef.current = setInterval(() => setRecordingTime((prev) => prev + 1), 1000);

      toast({
        title: "Recording Started",
        description: "Speak clearly to describe the issue",
      });
    } catch {
      toast({
        title: "Microphone Access Denied",
        description: "Please allow microphone access to use voice input",
        variant: "destructive",
      });
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        {!isRecording ? (
          <Button type="button" variant="outline" className="flex-1 gap-2" onClick={startRecording}>
            <Mic className="w-4 h-4" />
            Start Voice Input
          </Button>
        ) : (
          <Button type="button" variant="destructive" className="flex-1 gap-2 animate-pulse" onClick={stopRecording}>
            <Square className="w-4 h-4" />
            Stop Recording ({formatTime(recordingTime)})
          </Button>
        )}
      </div>

      {/* Real-time Transcript Display */}
      {isRecording && (transcript || interimTranscript) && (
        <div className="bg-muted rounded-xl p-4">
          <p className="text-sm font-medium mb-2">Live Transcription:</p>
          <Textarea
            aria-label="Live transcription"
            value={transcript + interimTranscript}
            readOnly
            className="min-h-[100px] bg-background"
            placeholder="Your speech will appear here..."
          />
          {interimTranscript && (
            <p className="text-xs text-muted-foreground mt-2">
              <span className="italic">{interimTranscript}</span> (processing...)
            </p>
          )}
        </div>
      )}

      {/* Final Transcript */}
      {!isRecording && transcript && (
        <div className="bg-success/10 border border-success/20 rounded-xl p-4">
          <p className="text-sm font-medium text-success mb-2">Captured Text:</p>
          <p className="text-sm">{transcript}</p>
        </div>
      )}
    </div>
  );
};
