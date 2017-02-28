/**
 * Copyright (C) 2015-2016 Data61, Commonwealth Scientific and Industrial Research Organisation (CSIRO).
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.data61.core.api

import au.csiro.data61.core.storage.SsdStorage
import au.csiro.data61.types._
import io.finch._
import org.joda.time.DateTime

import scala.language.postfixOps

import java.io.{ByteArrayInputStream, InputStream, FileInputStream}
import au.csiro.data61.core.drivers.{OctopusInterface, MatcherInterface}
import DataSetTypes._
import com.twitter.finagle.http.exp.Multipart
import com.twitter.finagle.http.exp.Multipart.{InMemoryFileUpload, OnDiskFileUpload}
import com.twitter.io.{Buf, BufReader}
import com.twitter.util.Await
import io.finch._
import org.json4s.jackson.JsonMethods._

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * SSD REST endpoints...
  *
  *  GET    /v1.0/ssd
  *  POST   /v1.0/ssd      -- description (string), ontologies (list ids), dataset (id)
  *  GET    /v1.0/ssd/:id
  *  POST   /v1.0/ssd/:id  -- description (string), ontologies (list ids), dataset (id)
  *  DELETE /v1.0/ssd/:id
  */
object SsdAPI extends RestAPI {

  val junkSSD = SemanticSourceDesc(
    id = Some(1),
    version = "0.1",
    name = "test",
    columns = List(SSDColumn(1, "col1"), SSDColumn(2, "col2")),
    attributes = List(
      SSDAttribute(1, "test", "hello", List(1, 2), "sql")
    ),
    ontology = List(1, 2, 3),
    semanticModel = None,
    mappings = None,
    dateCreated = DateTime.now(),
    dateModified = DateTime.now()
  )

  /**
    * Returns all dataset keys
    *
    * curl http://localhost:8080/v1.0/ssd
    */
  val ssdRoot: Endpoint[List[Int]] = get(APIVersion :: "ssd") {
    Ok(OctopusInterface.ssdKeys.flatten)
  }

  /**
    * Adds a new SSD as specified by the json body.
    *
    * {
    * }
    *
    * Returns a JSON SSD object with id.
    *
    */

  val ssdCreate: Endpoint[SemanticSourceDesc] = post(APIVersion :: "ssd" :: stringBody) {
    (body: String) =>
      Ok(junkSSD)
  }

  /**
    * Returns a JSON SSD object at id
    *
    * curl http://localhost:8080/v1.0/ssd/12354687
    */
  val ssdGet: Endpoint[SemanticSourceDesc] = get(APIVersion :: "ssd" :: int) {
    (id: Int) =>

      logger.debug(s"Get ssd id=$id")

      val ssd = Try { SsdStorage.get(Some(id)) }

      ssd match {
        case Success(Some(s))  =>
          Ok(s)
        case Success(None) =>
          NotFound(NotFoundException(s"SSD $id does not exist."))
        case Failure(err) =>
          BadRequest(BadRequestException(err.getMessage))
      }
  }

  /**
    * Patch a portion of a SSD. Only description and typeMap
    *
    * Returns a JSON SSD object at id
    *
    * curl -X POST -d 'description=This is the new description'
    * http://localhost:8080/v1.0/ssd/12354687
    */
  val ssdPatch: Endpoint[SemanticSourceDesc] = post(APIVersion :: "ssd" :: int :: stringBody) {

    (id: Int, body: String) =>

      logger.debug(s"Patching dataset id=$id")

      Ok(junkSSD)
  }

  /**
    * Deletes the ssd at position id.
    *
    * curl -X DELETE http://localhost:8080/v1.0/ssd/12354687
    */
  val ssdDelete: Endpoint[String] = delete(APIVersion :: "ssd" :: int) {
    (id: Int) =>
      Try(SsdStorage.remove(Some(id))) match {

        case Success(Some(_)) =>
          logger.debug(s"Deleted ssd $id")
          Ok(s"SSD $id deleted successfully.")

        case Success(None) =>
          logger.debug(s"Could not find ssd $id")
          NotFound(NotFoundException(s"SSD $id could not be found"))

        case Failure(err) =>
          logger.debug(s"Some other problem with deleting...")
          InternalServerError(InternalException(s"Failed to delete resource: ${err.getMessage}"))
      }
  }

  /**
    * Final endpoints for the Dataset endpoint...
    */
  val endpoints =
    ssdRoot :+:
      ssdCreate :+:
      ssdGet :+:
      ssdPatch :+:
      ssdDelete
}

/**
  * Object returned from any ssd request.
  *
  * @param description Description field written for the dataset
  */
case class SsdRequest(description: Option[String])
