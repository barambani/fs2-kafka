package fs2.kafka.vulcan

import cats.effect.IO
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import org.scalatest.funspec.AnyFunSpec
import vulcan.{AvroError, Codec}

final class AvroSerializerSpec extends AnyFunSpec {
  describe("AvroSerializer") {
    it("can create a serializer") {
      val serializer =
        avroSerializer[Int].using(avroSettings)

      assert(serializer.forKey.attempt.unsafeRunSync().isRight)
      assert(serializer.forValue.attempt.unsafeRunSync().isRight)
    }

    it("raises schema errors") {
      val codec: Codec[Int] =
        Codec.instance(
          Left(AvroError("error")),
          (_, _) => Left(AvroError("encode")),
          (_, _) => Left(AvroError("decode"))
        )

      val serializer =
        avroSerializer(codec).using(avroSettings)

      assert(serializer.forKey.attempt.unsafeRunSync().isLeft)
      assert(serializer.forValue.attempt.unsafeRunSync().isLeft)
    }

    it("toString") {
      assert {
        avroSerializer[Int].toString() startsWith "AvroSerializer$"
      }
    }
  }

  val schemaRegistryClient: MockSchemaRegistryClient =
    new MockSchemaRegistryClient()

  val schemaRegistryClientSettings: SchemaRegistryClientSettings[IO] =
    SchemaRegistryClientSettings[IO]("baseUrl")
      .withAuth(Auth.Basic("username", "password"))
      .withMaxCacheSize(100)
      .withCreateSchemaRegistryClient { (_, _, _) =>
        IO.pure(schemaRegistryClient)
      }

  val avroSettings: AvroSettings[IO] =
    AvroSettings(schemaRegistryClientSettings)
}
