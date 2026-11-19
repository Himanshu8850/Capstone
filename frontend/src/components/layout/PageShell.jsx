import { useState } from 'react';
import Header from './Header.jsx';
import Sidebar from './Sidebar.jsx';
import './PageShell.css';

/** Sidebars start open on desktop and closed on tablet and mobile (RSP-01..03). */
function initialSidebarOpen() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true;
  return !window.matchMedia('(max-width: 1023px)').matches;
}

/**
 * Page frame used by every screen: header, optional sidebar slots and the main content.
 * Pass `categories` and/or `brands` to show the sidebar and the header menu icon that toggles it.
 * Pass `aside` for a right-hand section that runs the full height of the page body (for example Related Reads); on
 * tablet and mobile it moves below the content.
 */
export default function PageShell({ categories, brands, aside, children }) {
  const hasSidebar = Boolean(categories || brands);
  const [sidebarOpen, setSidebarOpen] = useState(initialSidebarOpen);

  return (
    <div className="page-shell">
      <Header
        sidebarAvailable={hasSidebar}
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((open) => !open)}
      />
      <div className="page-shell__body">
        {hasSidebar ? <Sidebar open={sidebarOpen} categories={categories} brands={brands} /> : null}
        <main className="page-shell__main" id="main">
          {children}
        </main>
        {aside ?? null}
      </div>
    </div>
  );
}
