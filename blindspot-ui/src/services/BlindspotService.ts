export interface BlindspotServiceConfig {
  endpoint: string;
}

export class BlindspotSingletonService {
  private static instance: BlindspotSingletonService;
  private readonly endpoint: URL

  private constructor(endpoint: URL) {
    this.endpoint = endpoint;
  }

  public static initService(config: BlindspotServiceConfig): void {
    BlindspotSingletonService.instance = new BlindspotSingletonService(
      new URL(config.endpoint),
    );
  }

  public static getInstance(): BlindspotSingletonService {
    if (!BlindspotSingletonService.instance) throw new Error("BlindspotSingletonService not initialized.");
    return BlindspotSingletonService.instance;
  }

  private withPath(path: string): URL {
    return new URL(path, this.endpoint.toString())
  }

  mapToParams = (map: Map<string, string>) => new URLSearchParams(
    Array.from(map.entries()).map(([key, value]) => [key, String(value)])
  );

  public async fetchAuthenticated(
    path: string,
    paramsMap: Map<string, string> = new Map(),
    method: string = 'GET',
    body: any = undefined
  ): Promise<any> {
    const url: URL = this.withPath(`${path}?${this.mapToParams(paramsMap).toString()}`);
    // var headers = {...this.getAuthTokens(),};
    var headers = {} as any;

    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    const fetchOptions: RequestInit = {
      method,
      headers,
      ...(body !== undefined && {body: JSON.stringify(body)}),
    };

    let response = await fetch(url, fetchOptions);

    /*
    if (response.status === 401) {
      try {
        const refreshed = await this.keycloak.updateToken();
        if (refreshed) {
          console.log(`Token was refreshed.`);
          response = await fetch(url, fetchOptions);
        } else {
          console.error("Token is still valid.");
          response = await fetch(url, fetchOptions);
        }
      } catch (err) {
        console.error("Refreshing token has failed... do the login again with error: ", err);
        throw new Error("Reauthentication with Keycloak required.");
      }
    }
    */

    if (!response.ok) {
      throw new Error(`Request failed: ${response.status}`);
    }

    return response.json();
  }

  public async get(path: string, params: Map<string, string> = new Map()): Promise<any> {
    return this.fetchAuthenticated(path, params, 'GET')
  }

  public async post<A>(path: string, body: A, params: Map<string, string> = new Map()): Promise<any> {
    return this.fetchAuthenticated(path, params, 'POST', body)
  }

  public async put<A>(path: string, body: A, params: Map<string, string> = new Map()): Promise<any> {
    return this.fetchAuthenticated(path, params, 'PUT', body)
  }
}

class BlindspotService {
  get = (path: string, params: Map<string, string> = new Map()) => BlindspotSingletonService.getInstance().get(path, params)

  post<A>(path: string, body: A, params: Map<string, string> = new Map()) {
    return BlindspotSingletonService.getInstance().post(path, body, params)
  }

  put<A>(path: string, body: A, params: Map<string, string> = new Map()) {
    return BlindspotSingletonService.getInstance().put(path, body, params)
  }
}

export interface Package {
  name: string
  value: string
  priority: number
}

export interface Country {
  name: string
  value: string
  priority: number
}

export interface Platform {
  name: string
  value: Set<string>
  priority: number
}

export interface ServerConfig {
  packages: Package[],
  countries: Country[],
  platforms: Platform[]
}

export interface Row {
  id: string
  title: string
  rank: number,
  releaseYear: number
  imdbVotes?: number
  imdbScore?: number
  tmdbPopularity?: number,
  tmdbScore?: number,
  tomatoMeter?: number,
  providerTags: Set<string>,
  kind: ItemKind
}

export enum ItemKind {
  Movie = 'Movie',
  Show = 'Show',
}

export enum GridMode {
  Platform = "Platform",
  Country = "Country",
  CountryPlatform = "CountryPlatform",
  PlatformCountry = "PlatformCountry",
}

export type Grid = Row[]

export interface GridOptions {
  query: string
  showMovies: boolean
  showShows: boolean
}

export const defaultGridOptions: GridOptions = {
  query: "",
  showMovies: true,
  showShows: true,
}

export class BlindspotAPI extends BlindspotService {
  private static api: BlindspotAPI = new BlindspotAPI();

  static async config(): Promise<ServerConfig> {
    return this.api.get(`/config`)
  }

  static async grid(options: GridOptions = defaultGridOptions): Promise<Grid> {
    const {query, showMovies, showShows} = options;
    const map = new Map<string, string>([
      ['kind',
        [(showMovies ? "Movie" : ""), (showShows ? "Show" : "")].filter(x => x != "").join("-")
      ],
      ['query', query ? query : ""],
    ].filter(x => x[1] !== "") as any);

    return this.api.get(`/grid`, map)
  }
}
