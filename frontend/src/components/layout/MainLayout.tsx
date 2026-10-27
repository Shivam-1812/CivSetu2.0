import { Navbar } from "@/components/layout/Navbar";
import { Outlet } from "react-router";

export function MainLayout() {
  return (
    <div className="min-h-screen bg-background font-sans antialiased">
      <Navbar />
      <div className="pt-16">
        <Outlet />
      </div>
    </div>
  );
}
