package com.ligadata.metadataapiservice

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import akka.io.IO
import spray.routing.RequestContext
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import scala.util.{ Success, Failure }
import com.ligadata.MetadataAPI._
import com.ligadata.Serialize._

import scala.util.control._
import org.apache.log4j._

object GetObjectsService {
	case class Process(apiArgListJson: String)
}

class GetObjectsService(requestContext: RequestContext, userid:Option[String], password:Option[String], cert:Option[String]) extends Actor {

  import GetObjectsService._
  
  implicit val system = context.system
  import system.dispatcher
  val log = Logging(system, getClass)
  
  
  val loggerName = this.getClass.getName
  val logger = Logger.getLogger(loggerName)
 // logger.setLevel(Level.TRACE);

  val APIName = "GetObjects"

  def receive = {
    case Process(apiArgListJson: String) =>
      process(apiArgListJson)
      context.stop(self)
  }

  def GetObjectDef(arg: MetadataApiArg): String = {
    var resultStr:String = ""
    var nameSpace = "system"
    var version = "-1"
    var formatType = "JSON"
    var apiResult:String = ""

    if( arg.NameSpace != null ){
      nameSpace = arg.NameSpace
    }
    if( arg.Version != null ){
      version = arg.Version
    }
    if( arg.FormatType != null ){
      formatType = arg.FormatType
    }
    
    val objectName = (nameSpace + arg.Name + version).toLowerCase
    if (!MetadataAPIImpl.checkAuth(userid,password,cert, MetadataAPIImpl.getPrivilegeName("get",arg.ObjectType))) {
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetObjects",objectName,"Failed","unknown","READ not allowed for this user")
	      requestContext.complete(new ApiResult(-1, APIName, null, "Error:READ not allowed for this user").toString )
    }

    arg.ObjectType match {
      case "model" => {
	      apiResult = MetadataAPIImpl.GetModelDef(nameSpace,arg.Name,formatType,version)
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetModelDef",objectName,"Finished","unknown",apiResult)
      }
      case "message" => {
	      apiResult = MetadataAPIImpl.GetMessageDef(nameSpace,arg.Name,formatType,version)
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetMessageDef",objectName,"Finished","unknown",apiResult)
      }
      case "container" => {
	      apiResult = MetadataAPIImpl.GetContainerDef(nameSpace,arg.Name,formatType,version)
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetContainerDef",objectName,"Finished","unknown",apiResult)
      }
      case "function" => {
	      apiResult = MetadataAPIImpl.GetFunctionDef(nameSpace,arg.Name,formatType,version)
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetFunctionDef",objectName,"Finished","unknown",apiResult)
      }
      case "concept" => {
	      apiResult = MetadataAPIImpl.GetConceptDef(nameSpace,arg.Name,formatType,version)
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetConceptDef",objectName,"Finished","unknown",apiResult)
      }
      case "type" => {
	      apiResult = MetadataAPIImpl.GetTypeDef(nameSpace,arg.Name,formatType,version)
	      MetadataAPIImpl.logAuditRec(userid,Some("get"),"GetTypeDef",objectName,"Finished","unknown",apiResult)
      }
    }
    apiResult
  }

  def process(apiArgListJson: String) = {
    
    logger.debug(APIName + ":" + apiArgListJson)

    val apiArgList = JsonSerializer.parseApiArgList(apiArgListJson)
    val arguments = apiArgList.ArgList
    var resultStr:String = ""

    if ( arguments.length > 0 ){
      var loop = new Breaks
      loop.breakable {
	      arguments.foreach(arg => {
	        if (arg.ObjectType == null ) {
	          resultStr = APIName + ":Error: The value of object type can't be null"
	          loop.break
	        } 
          
          if (arg.Name == null ) {
	          resultStr = APIName + ":Error: The value of object name can't be null"
	          loop.break
	        } else {
	          resultStr = resultStr + GetObjectDef(arg)
	        }
	      })
      }
    } else {
      resultStr = APIName + ":No arguments passed to the API, nothing much to do"
    }
    
    logger.debug("resultStr => " + resultStr)
    requestContext.complete(resultStr)
  }
}
