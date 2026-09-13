export type WebsiteMetadata = {
  title: string
  description: string
  canonicalUrl: string
  robots: 'index,follow' | 'noindex,nofollow'
  openGraphTitle: string
  openGraphDescription: string
  openGraphUrl: string
}

export function websiteMetadata({
  origin,
  pathname,
  title,
  description,
  previewMode,
}: {
  origin: string
  pathname: string
  title: string
  description: string
  previewMode: boolean
}): WebsiteMetadata {
  const canonicalUrl = new URL(pathname, origin).toString()
  return {
    title,
    description,
    canonicalUrl,
    robots: previewMode ? 'noindex,nofollow' : 'index,follow',
    openGraphTitle: title,
    openGraphDescription: description,
    openGraphUrl: canonicalUrl,
  }
}
