import type { PulseAttributes } from '../pulse.interface';
import { parseUrl } from './url-helper';
import { ATTRIBUTE_KEYS } from '../pulse.constants';

export interface GraphQLOperationData {
  operationName?: string;
  operationType?: string;
}

export function isGraphQLRequest(url: string): boolean {
  if (!url || typeof url !== 'string') {
    return false;
  }
  return url.toLowerCase().includes('graphql');
}

function parseGraphQLQuery(query: string): GraphQLOperationData {
  if (!query || typeof query !== 'string') {
    return {};
  }

  // Named query: "query MyQuery { ... }" or "mutation CreateUser { ... }"
  const namedQueryRe =
    /^(?:\s*)(query|mutation|subscription)(?:\s+)(\w+)(?:\s*)[{(]/;
  const namedMatch = query.match(namedQueryRe);
  if (namedMatch && namedMatch.length >= 3) {
    return {
      operationType: namedMatch[1],
      operationName: namedMatch[2],
    };
  }

  // Unnamed query: "query { ... }" or "mutation { ... }"
  const unnamedQueryRe = /^(?:\s*)(query|mutation|subscription)(?:\s*)[{(]/;
  const unnamedMatch = query.match(unnamedQueryRe);
  if (unnamedMatch && unnamedMatch.length >= 2) {
    return {
      operationType: unnamedMatch[1],
      operationName: undefined,
    };
  }

  return {};
}

function extractFromBody(body: string): GraphQLOperationData {
  try {
    const payload = JSON.parse(body);
    let data: GraphQLOperationData = {
      operationName: payload?.operationName,
      operationType: payload?.operation,
    };

    // Fallback: If operationName/operation not in payload, parse from query string
    if ((!data.operationName || !data.operationType) && payload?.query) {
      const parsedQuery = parseGraphQLQuery(payload.query);
      data = {
        operationName: data.operationName || parsedQuery.operationName,
        operationType: data.operationType || parsedQuery.operationType,
      };
    }

    return data;
  } catch {
    return {};
  }
}

function extractFromQueryParams(url: string): GraphQLOperationData {
  try {
    const parsedUrl = parseUrl(url);
    if (!parsedUrl) {
      return {};
    }
    return {
      operationName: parsedUrl.searchParams.get('operationName') || undefined,
      operationType: parsedUrl.searchParams.get('operation') || undefined,
    };
  } catch {
    return {};
  }
}

export function updateAttributesWithGraphQLData(
  url: string,
  body?: Document | XMLHttpRequestBodyInit | null
): PulseAttributes {
  if (!isGraphQLRequest(url)) {
    return {};
  }

  let data: GraphQLOperationData = {};

  if (body && typeof body === 'string') {
    data = extractFromBody(body);
  }
  if (!data.operationName && !data.operationType) {
    data = extractFromQueryParams(url);
  }

  const attributes: PulseAttributes = {};
  if (data.operationName) {
    attributes[ATTRIBUTE_KEYS.GRAPHQL_OPERATION_NAME] = data.operationName;
  }
  if (data.operationType) {
    attributes[ATTRIBUTE_KEYS.GRAPHQL_OPERATION_TYPE] = data.operationType;
  }

  return attributes;
}
