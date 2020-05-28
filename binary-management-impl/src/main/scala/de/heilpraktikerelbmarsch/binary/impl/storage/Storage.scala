package de.heilpraktikerelbmarsch.binary.impl.storage

import java.io.{File, FileInputStream, InputStream}

import akka.Done
import io.minio.{MinioClient, PutObjectOptions}
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

trait Storage {

  def savePatientFile(patientNumber: String,
                      caseNumber: Option[String],
                      file: File): Future[(String,String)]

  def saveSystemFile(file: File,
                     replace: Boolean = false): Future[(String,String)]

  def getPatientFile(patientNumber: String,
                     path: String): Future[InputStream]

  def getSystemFile(pathAndName: String): Future[InputStream]

//  def saveFile(patientNumber: String,
//                files: File): Future[Done]
//
//  def saveFiles(files: Seq[File]): Future[Done]
//
//  def saveSystemFile(file: File,
//                     replace: Boolean = false): Future[Done]
//
//  def saveFileInDirectory(director: String,
//                          file: File): Future[Done]
//
//  def getFile(patientNumber: String, caseNumber: String): Future[InputStream]

}


case class MinioStorageImpl(endpoint: String,
                            accessKey: String,
                            secretKey: String)(implicit ec: ExecutionContext) extends Storage {

  private val s3Client = new MinioClient(endpoint,accessKey,secretKey)

  private val patientBucket = (str: String) => s"patient-${str.trim.toLowerCase}"

  private def upload(bucket: String, prePath: String, file: File): Future[String] = Future{
    val path = prePath+file.getName
    s3Client.putObject(bucket,path,new FileInputStream(file), new PutObjectOptions(file.length(),-1))
    path
  }
//
//  def saveFiles(patientNumber: String,
//                caseNumber: String,
//                files: File*): Future[Done] = {
//    if( !s3Client.bucketExists( patientBucket(patientNumber) ) ){
//      s3Client.makeBucket(patientBucket(patientNumber))
//    }
//    val path = s"case-${caseNumber.trim.toLowerCase}/${DateTime.now().toString("dd-MM-yy")}/"
//    upload(patientBucket(patientNumber),path,files:_*)
//  }
//
//
//  def saveFiles(patientNumber: String,
//                files: File*): Future[Done] = {
//    val path = s"${DateTime.now().toString("dd-MM-yy")}/"
//    if(!s3Client.bucketExists(patientBucket(patientNumber))) s3Client.makeBucket(patientBucket(patientNumber))
//    upload(patientBucket(patientNumber),path,files:_*)
//  }
//
//  def saveFiles(files: Seq[File]): Future[Done] = {
//    val bucket = "util"
//    if(!s3Client.bucketExists(bucket)) s3Client.makeBucket(bucket)
//    upload(bucket,s"${DateTime.now().toString("dd-MM-yy")}/",files:_*)
//  }
//
//  def saveSystemFile(file: File, replace: Boolean): Future[Done] = {
//    val bucket = "system"
//    if(!s3Client.bucketExists(bucket)) s3Client.makeBucket(bucket)
//    if(replace)
//      upload(bucket,"",file)
//    else
//      upload(bucket,DateTime.now().toString("ddMMyy_HHmm-"),file)
//  }
//
//  def saveFileInDirectory(director: String, file: File): Future[Done] = {
//    val bucket = "directory"
//    if(!s3Client.bucketExists(bucket)) s3Client.makeBucket(bucket)
//    upload(bucket,s"${director.trim}/",file)
//  }


  override def savePatientFile(patientNumber: String, caseNumber: Option[String], file: File): Future[(String,String)] = {
    val bucket = patientBucket(patientNumber.trim.toLowerCase)
    if(!s3Client.bucketExists(bucket)) s3Client.makeBucket(bucket)
    val path = caseNumber.map(r => s"case-${r.trim.toLowerCase}/").getOrElse("")+s"${DateTime.now().toString("dd-MM-yy")}/"
    upload(bucket,path,file).map(u => bucket -> u)
  }

  override def saveSystemFile(file: File, replace: Boolean): Future[(String,String)] = {
    val bucket = "system"
    if(!s3Client.bucketExists(bucket)) s3Client.makeBucket(bucket)
    val path = if(replace) "" else s"${DateTime.now().toString("ddMMyy_HHmm-")}"
    upload(bucket,path,file).map(i => bucket -> i)
  }

  override def getPatientFile(patientNumber: String,
                              path: String): Future[InputStream] = Future(s3Client.getObject(patientBucket(patientNumber),path))

  override def getSystemFile(pathAndName: String): Future[InputStream] = Future(s3Client.getObject("system",pathAndName))
}