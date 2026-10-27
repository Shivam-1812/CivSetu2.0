import { Component, ReactNode } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Catches runtime errors thrown by the Google Maps component tree and renders
 * a friendly fallback instead of letting React unmount the entire page
 * (which causes the white / blank screen the user sees).
 */
export class MapErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: { componentStack: string }) {
    console.error(
      "[MapErrorBoundary] Map crashed:",
      error,
      info.componentStack,
    );
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="w-full h-[500px] rounded-2xl border border-border/50 bg-muted/30 flex flex-col items-center justify-center gap-4 text-center p-8">
          <AlertTriangle className="w-12 h-12 text-destructive/60" />
          <div>
            <h3 className="text-lg font-semibold mb-1">Map failed to load</h3>
            <p className="text-sm text-muted-foreground max-w-sm">
              There was a problem loading the map. This is usually a temporary
              Google Maps API issue.
            </p>
            {this.state.error && (
              <p className="mt-2 text-xs text-muted-foreground/70 font-mono">
                {this.state.error.message}
              </p>
            )}
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={this.handleRetry}
            className="gap-2"
          >
            <RefreshCw className="w-4 h-4" />
            Try Again
          </Button>
        </div>
      );
    }

    return this.props.children;
  }
}
