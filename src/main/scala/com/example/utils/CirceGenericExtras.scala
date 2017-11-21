package com.example.utils

import shapeless._
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}


object CirceValueClass {


  implicit def encoderValueClass[T <: AnyVal, V](implicit
                                                 g: Lazy[Generic.Aux[T, V :: HNil]],
                                                 e: Encoder[V]
                                                ): Encoder[T] = Encoder.instance { value ⇒
    e(g.value.to(value).head)
  }

  implicit def decoderValueClass[T <: AnyVal, V](implicit
                                                 g: Lazy[Generic.Aux[T, V :: HNil]],
                                                 d: Decoder[V]
                                                ): Decoder[T] = Decoder.instance { cursor ⇒
    d(cursor).map { value ⇒
      g.value.from(value :: HNil)
    }
  }
}

object CirceValueClassKeyEncoder {

  implicit def valueClassKeyEncoder[K <: AnyVal, KV](implicit
                                                     g: Lazy[Generic.Aux[K, KV :: HNil]],
                                                     e: KeyEncoder[KV]): KeyEncoder[K] =
    e.contramap[K](g.value.to(_).head)

  implicit def valueClassKeyDecoder[K <: AnyVal, KV](implicit
                                                     g: Lazy[Generic.Aux[K, KV :: HNil]],
                                                     e: KeyDecoder[KV]): KeyDecoder[K] =
    e.map { value ⇒
      g.value.from(value :: HNil)
    }
}
