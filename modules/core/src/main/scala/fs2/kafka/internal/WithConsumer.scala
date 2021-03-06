/*
 * Copyright 2018-2019 OVO Energy Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fs2.kafka.internal

import cats.effect.{Blocker, Concurrent, ContextShift, Resource}
import cats.implicits._
import fs2.kafka.{KafkaByteConsumer, ConsumerSettings}
import fs2.kafka.internal.syntax._

private[kafka] sealed abstract class WithConsumer[F[_]] {
  def apply[A](f: KafkaByteConsumer => F[A]): F[A]
}

private[kafka] object WithConsumer {
  def apply[F[_], K, V](
    settings: ConsumerSettings[F, K, V]
  )(
    implicit F: Concurrent[F],
    context: ContextShift[F]
  ): Resource[F, WithConsumer[F]] = {
    val blockerResource =
      settings.blocker
        .map(Resource.pure[F, Blocker])
        .getOrElse(Blockers.consumer)

    blockerResource.flatMap { blocker =>
      Resource[F, WithConsumer[F]] {
        settings.createConsumer
          .flatMap(Synchronized[F].of)
          .map { synchronizedConsumer =>
            val withConsumer =
              new WithConsumer[F] {
                override def apply[A](f: KafkaByteConsumer => F[A]): F[A] =
                  synchronizedConsumer.use { consumer =>
                    context.blockOn(blocker) {
                      f(consumer)
                    }
                  }
              }

            val close =
              withConsumer { consumer =>
                F.delay(consumer.close(settings.closeTimeout.asJava))
              }

            (withConsumer, close)
          }
      }
    }
  }
}
