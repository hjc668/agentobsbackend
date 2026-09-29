/**
 * Mixin for remote data fetching with AbortController support
 * Replaces React's useRemote hook pattern
 */
export default {
  data() {
    return {
      remoteData: null,
      remoteLoading: true,
      remoteError: null,
      _abortController: null
    };
  },

  methods: {
    /**
     * Load remote data
     * @param {Function} loadFn - Async function that receives AbortSignal and returns data
     */
    async loadRemote(loadFn) {
      // Abort previous request
      if (this._abortController) {
        this._abortController.abort();
      }

      this._abortController = new AbortController();
      this.remoteLoading = true;
      this.remoteError = null;

      try {
        this.remoteData = await loadFn(this._abortController.signal);
      } catch (err) {
        if (err.name !== "AbortError") {
          this.remoteError = err.message || "Failed to load data";
          this.remoteData = null;
        }
      } finally {
        if (!this._abortController.signal.aborted) {
          this.remoteLoading = false;
        }
      }
    },

    /**
     * Abort current request
     */
    abortRemote() {
      if (this._abortController) {
        this._abortController.abort();
        this._abortController = null;
      }
    }
  },

  beforeDestroy() {
    this.abortRemote();
  }
};
