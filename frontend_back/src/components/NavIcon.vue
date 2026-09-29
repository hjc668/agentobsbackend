<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <!-- home -->
    <template v-if="name === 'home'">
      <path d="M3 11.5 12 4l9 7.5" />
      <path d="M5 10.5V20h14v-9.5" />
      <path d="M9 20v-6h6v6" />
    </template>
    <!-- dashboard -->
    <template v-else-if="name === 'dashboard'">
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </template>
    <!-- trace -->
    <template v-else-if="name === 'trace'">
      <path d="M5 5h5v5H5zM14 14h5v5h-5zM14 5h5M5 16h5M12 7v9" />
    </template>
    <!-- clock -->
    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </template>
    <!-- users -->
    <template v-else-if="name === 'users'">
      <path d="M16 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 20v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
    </template>
    <!-- bell -->
    <template v-else-if="name === 'bell'">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
      <path d="M10 21h4" />
    </template>
    <!-- file -->
    <template v-else-if="name === 'file'">
      <path d="M5 3h10l4 4v14H5z" />
      <path d="M15 3v5h4M8 13h8M8 17h6" />
    </template>
    <!-- terminal -->
    <template v-else-if="name === 'terminal'">
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="m7 9 3 3-3 3M13 15h4" />
    </template>
    <!-- score -->
    <template v-else-if="name === 'score'">
      <rect x="3" y="3" width="18" height="18" rx="3" />
      <path d="m8 16 8-8M8 9h.01M16 15h.01" />
    </template>
    <!-- bulb -->
    <template v-else-if="name === 'bulb'">
      <path d="M9 18h6M10 22h4M8.5 14.5A7 7 0 1 1 15.5 14.5c-.8.7-1.3 1.7-1.5 2.5h-4c-.2-.8-.7-1.8-1.5-2.5Z" />
    </template>
    <!-- clipboard -->
    <template v-else-if="name === 'clipboard'">
      <rect x="5" y="4" width="14" height="17" rx="2" />
      <path d="M9 4V2h6v2M9 12l2 2 4-4" />
    </template>
    <!-- database -->
    <template v-else-if="name === 'database'">
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" />
    </template>
    <!-- beaker -->
    <template v-else-if="name === 'beaker'">
      <path d="M9 3h6M10 3v6l-5 9a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-5-9V3" />
      <path d="M7 16h10" />
    </template>
    <!-- settings -->
    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3" />
      <path
        d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9A1.7 1.7 0 0 0 21 10h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"
      />
    </template>
    <!-- search -->
    <template v-else-if="name === 'search'">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </template>
    <!-- support -->
    <template v-else-if="name === 'support'">
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="3" />
      <path d="m5.6 5.6 4.3 4.3M14.1 14.1l4.3 4.3M18.4 5.6l-4.3 4.3M9.9 14.1l-4.3 4.3" />
    </template>
    <!-- calendar -->
    <template v-else-if="name === 'calendar'">
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4M17 3v4M3 10h18" />
    </template>
    <!-- menu -->
    <template v-else-if="name === 'menu'">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </template>
  </svg>
</template>

<script>
export default {
  name: "NavIcon",
  props: {
    name: {
      type: String,
      required: true,
      validator: val =>
        [
          "home",
          "dashboard",
          "trace",
          "clock",
          "users",
          "bell",
          "file",
          "terminal",
          "score",
          "bulb",
          "clipboard",
          "database",
          "beaker",
          "settings",
          "search",
          "support",
          "calendar",
          "menu"
        ].includes(val)
    },
    size: {
      type: Number,
      default: 16
    }
  }
};
</script>
