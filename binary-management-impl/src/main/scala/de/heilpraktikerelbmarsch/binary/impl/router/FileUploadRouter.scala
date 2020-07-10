package de.heilpraktikerelbmarsch.binary.impl.router

import java.io.File

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import de.heilpraktikerelbmarsch.binary.api.adt.request.PatientDataUploadForm
import de.heilpraktikerelbmarsch.binary.impl.repository.BinaryRepository
import de.heilpraktikerelbmarsch.binary.impl.storage.Storage
import play.api.data.Form
import play.api.data.Form._
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{DefaultActionBuilder, PlayBodyParsers, Results}
import play.api.routing.Router
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}
import play.api.routing.sird._

import scala.concurrent.{ExecutionContext, Future}

class FileUploadRouter(action: DefaultActionBuilder,
                       parser: PlayBodyParsers,
                       binaryRepository: BinaryRepository)(implicit val exCtx: ExecutionContext) {

  // A Play FilePartHandler[T] creates an Accumulator (similar to Akka Stream's Sinks)
  // for each FileInfo in the multipart request.
  private def fileHandler: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType, _) =>
      val tempFile = {
        // create a temp file in the `target` folder
        val f = new java.io.File("./target/file-upload-data/uploads", filename).getAbsoluteFile
        // make sure the sub-folders inside `target` exist.
        f.getParentFile.mkdirs()
        if(f.exists()) f.delete()
        f
      }
      val sink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(tempFile.toPath)
      val acc: Accumulator[ByteString, IOResult] = Accumulator(sink)
      acc.map {
        case akka.stream.IOResult(_, _) =>
          FilePart(partName, filename, contentType, tempFile)
      }

  }

  val uploadForm = Form(mapping(
    "patient-number" -> nonEmptyText,
    "case-number" -> optional(text),
    "description" -> optional(text)
  )(PatientDataUploadForm.apply)(PatientDataUploadForm.unapply))

  /**
   * Rückgabe für den upload ist datei name und id zum finden des bildes!
   */
  val router = Router.from {
    case POST(/*p"/patient/file"*/route) if route.path.contains("/patient/file") =>
      action.async( parser.multipartFormData(fileHandler) ) { request =>
        val t = uploadForm.bindFromRequest()(request)
        t.fold(
          hasErrors => Future.successful(Results.BadRequest(s"Error in upload!\n${hasErrors.errors.mkString("\n")}")),
          ok => {
            Future.sequence(request.body.files.map{u =>
              binaryRepository.addPatientFile(ok.patientNumber,ok.caseNumber,u.ref,ok.description).map(r => r -> u.filename)
            }).map{a => Results.Ok( Json.toJson(a.map(r => Json.toJson( Map("fileName" -> Json.toJson(r._2), "id" -> Json.toJson(r._1) ) ) ) ) ) }
          }
        )
      }
  }

}
