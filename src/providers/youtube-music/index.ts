import type { MusicProvider, PlaybackResolution, SearchQuery, SearchResult } from "@/lib/contracts/music";
import { searchYoutubeMusic } from "./searchService";
import { resolveYoutubeMusicPlayback } from "./playbackResolver";
import { checkYoutubeMusicHealth } from "./providerHealth";

export const youtubeMusicProvider: MusicProvider = {
  id: "youtube-music",

  search(query: SearchQuery): Promise<SearchResult> {
    return searchYoutubeMusic(query);
  },

  resolvePlayback(providerTrackId: string): Promise<PlaybackResolution> {
    return resolveYoutubeMusicPlayback(providerTrackId);
  },

  health() {
    return checkYoutubeMusicHealth();
  },
};
