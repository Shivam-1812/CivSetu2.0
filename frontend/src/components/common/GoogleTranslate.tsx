import { useEffect, useRef } from "react";

declare global {
  interface Window {
    google: any;
    googleTranslateElementInit: () => void;
  }
}

export const GoogleTranslate = () => {
  const isInitialized = useRef(false);

  useEffect(() => {
    // defined the function that google translate script looks for
    window.googleTranslateElementInit = () => {
      // Only init if we haven't already or if the element exists but is empty
      const element = document.getElementById("google_translate_element");
      if (element && window.google?.translate) {
        new window.google.translate.TranslateElement(
          {
            pageLanguage: "en",
            includedLanguages: "en,hi,bn,te,mr,ta,ur,gu,kn,ml,pa,es,fr,de", // Added some international langs
            layout:
              window.google.translate.TranslateElement.InlineLayout.HORIZONTAL,
            autoDisplay: false,
          },
          "google_translate_element"
        );
        isInitialized.current = true;
      }
    };

    // check if script is already present
    const existingScript = document.getElementById("google-translate-script");

    if (!existingScript) {
      const script = document.createElement("script");
      script.id = "google-translate-script";
      script.src =
        "https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit";
      script.async = true;
      document.body.appendChild(script);
    } else {
      // If script is already loaded, manually re-init immediately
      // This handles the route change case where component re-mounts
      if (window.google?.translate) {
        window.googleTranslateElementInit();
      }
    }
  }, []);

  return (
    <div className="google-translate-container">
      <div id="google_translate_element" />
    </div>
  );
};
