import akka.actor.ActorSystem
import play.api.libs.json._
import sttp.capabilities
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.model.StatusCode

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// Case classes and Json formatters
case class Movie(id: Int, title: String, genres: Seq[Int], popularity: Double, voteAverage: Double, overview: String, releaseDate: String, voteCount: Int)

case class Genre(id: Int, name: String)

case class Emotion(emotion: String, confidence: Double)

case class UserPreference(genres: Seq[Int], releaseYear: Option[Int], minimumRating: Option[Double], minPopularity: Option[Double], minVotes: Option[Int])

object Movie {
  implicit val movieReads: Reads[Movie] = Json.reads[Movie]
}

object Genre {
  implicit val genreReads: Reads[Genre] = Json.reads[Genre]
}

object Emotion {
  implicit val emotionFormat: Format[Emotion] = Json.format[Emotion]
}

// Main object
object MovieRecommendationSystem {

  private val apiKeyTMDB = "YOUR_TMDB_API_KEY"
  private val apiKeyOpenAI = "YOUR_OPENAI_API_KEY"
  private val tmdbUrl = "https://api.themoviedb.org/3"
  private val openAIUrl = "https://api.openai.com/v1/engines/davinci-codex/completions"

  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem()
    implicit val backend: SttpBackend[Future, AkkaStreams with capabilities.WebSockets] = AkkaHttpBackend()

    val futureResult = for {
      allGenres <- getAllGenres
      emotion <- getUserEmotion
      _ = println(s"User emotion: ${emotion.emotion}")

      userPreferences = UserPreference(Seq(35, 18), Some(2000), Some(7.0), Some(50), Some(1000))
      recommendations <- getEmotionBasedMovieRecommendations(emotion, userPreferences, allGenres)
    } yield {
      println("Movie recommendations:")
      recommendations.foreach(println)
    }

    futureResult.recover {
      case e =>
        println(s"Error: ${e.getMessage}")
        system.terminate()
    }.map { _ =>
      system.terminate()
    }
  }

  private def getUserEmotion()(implicit system: ActorSystem, backend: SttpBackend[Future, Any]): Future[Emotion] = {
    val prompt = "The user's emotion is:"
    val maxTokens = 5
    val n = 1
    val data = Json.obj(
      "prompt" -> prompt,
      "max_tokens" -> maxTokens,
      "n" -> n
    )

    val request = basicRequest
      .header("Authorization", s"Bearer $apiKeyOpenAI")
      .contentType("application/json")
      .post(uri"$openAIUrl")
      .body(data.toString())

    apiCall(request).map { json =>
      val choices = (json \ "choices").as[JsArray].value
      val emotionText = (choices.head \ "text").as[String].trim
      Emotion(emotionText, 1.0) // Confidence is set to 1.0 as an example
    }
  }

  private def getEmotionBasedMovieRecommendations(emotion: Emotion, userPreferences: UserPreference, allGenres: Map[Int, String])(implicit system: ActorSystem, backend: SttpBackend[Future, Any]): Future[Seq[Movie]] = {
    val genreIds = userPreferences.genres.mkString(",")
    val releaseYear = userPreferences.releaseYear.getOrElse(2000)
    val minimumRating = userPreferences.minimumRating.getOrElse(7.0)
    val minVotes = userPreferences.minVotes.getOrElse(1000)

    val uri = uri"$tmdbUrl/discover/movie?api_key=$apiKeyTMDB&with_genres=$genreIds&primary_release_year.gte=$releaseYear&vote_average.gte=$minimumRating&sort_by=popularity.desc&page=1&vote_count.gte=$minVotes"

    apiCall(basicRequest.get(uri)).map { json =>
      val moviesJson = (json \ "results").as[JsArray].value

      val candidateMovies = moviesJson.map { movieJson =>
        movieJson.as[Movie]
      }

      val emotionWeights = Map(
        "happy" -> (0.5, 0.4, 0.1),
        "sad" -> (0.4, 0.5, 0.1),
        "angry" -> (0.6, 0.3, 0.1),
        "fearful" -> (0.4, 0.5, 0.1),
        "surprised" -> (0.5, 0.4, 0.1),
        "neutral" -> (0.4, 0.4, 0.2)
      )

      val weights = emotionWeights.getOrElse(emotion.emotion, emotionWeights("neutral"))

      val rankedMovies = candidateMovies.map { movie =>
        val score = movie.popularity * weights._1 + movie.voteAverage * weights._2 + (movie.voteCount / 1000) * weights._3
        (movie, score)
      }.sortWith((a, b) => a._2 > b._2)
        .take(1500)

      val diverseRecommendations = rankedMovies.groupBy(_._1.genres)
        .flatMap {
          case (_, genreMovies) => genreMovies.take(5)
        }
        .toList
        .sortBy(-_._1.popularity)
        .take(20)
        .map(_._1)

      diverseRecommendations
    }
  }

  private def getAllGenres()(implicit system: ActorSystem, backend: SttpBackend[Future, Any]): Future[Map[Int, String]] = {
    val request = uri"$tmdbUrl/genre/movie/list?api_key=$apiKeyTMDB"
    apiCall(basicRequest.get(request)).map { json =>
      val genres = (json \ "genres").as[Seq[Genre]]
      genres.map(genre => genre.id -> genre.name).toMap
    }
  }

  private def apiCall(request: Request[Either[String, String], Any])(implicit backend: SttpBackend[Future, Any]): Future[JsValue] = {
    request.send(backend).map { r =>
      r.body match {
        case Right(body) => Json.parse(body)
        case Left(_) => throw ApiException(s"Error fetching data from API: ${r.code}", r.code)
      }
    }
  }

  case class ApiException(message: String, statusCode: StatusCode) extends Exception(message)
}
