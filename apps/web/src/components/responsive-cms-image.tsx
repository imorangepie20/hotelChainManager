import type { ComponentPropsWithoutRef } from 'react'
import type { ResponsiveMediaVariant } from '../lib/content-page'

type ResponsiveCmsImageProps = ComponentPropsWithoutRef<'img'> & {
  imageVariants?: ResponsiveMediaVariant[]
  sizes: string
}

export function ResponsiveCmsImage({ imageVariants, sizes, ...image }: ResponsiveCmsImageProps) {
  if (!imageVariants?.length) return <img {...image} />
  const srcSet = imageVariants.map(variant => `${variant.deliveryUrl} ${variant.targetWidth}w`).join(', ')
  return <picture className="responsive-cms-picture">
    <source type="image/webp" srcSet={srcSet} sizes={sizes} />
    <img {...image} />
  </picture>
}
