package com.ligadata.ZooKeeper

import com.ligadata.Serialize._
import com.ligadata.MetadataAPI._
import com.ligadata.olep.metadata._
import org.apache.curator.RetryPolicy
import org.apache.curator.framework._
import org.apache.curator.framework.recipes.cache._
import org.apache.curator.framework.api._
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils._
import java.util.concurrent.locks._
import org.apache.log4j._
import scala.util.control.Breaks._

object ZooKeeperListener {

  private type OptionMap = Map[Symbol, Any]

  val loggerName = this.getClass.getName
  lazy val logger = Logger.getLogger(loggerName)
  var zkc: CuratorFramework = null

  private def ProcessData(newData: ChildData, UpdOnLepMetadataCallback: (ZooKeeperTransaction, MdMgr) => Unit) = {
    try {
      val data = newData.getData()
      if (data != null) {
        val receivedJsonStr = new String(data)
        logger.debug("New data received => " + receivedJsonStr)
        val zkMessage = JsonSerializer.parseZkTransaction(receivedJsonStr, "JSON")
        MetadataAPIImpl.UpdateMdMgr(zkMessage)
        if (UpdOnLepMetadataCallback != null)
          UpdOnLepMetadataCallback(zkMessage, com.ligadata.olep.metadata.MdMgr.GetMdMgr)
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

  def CreateNodeIfNotExists(zkcConnectString: String, znodePath: String) = {
    CreateClient.CreateNodeIfNotExists(zkcConnectString, znodePath)
  }

  def CreateListener(zkcConnectString: String, znodePath: String, UpdOnLepMetadataCallback: (ZooKeeperTransaction, MdMgr) => Unit) = {
    try {
      zkc = CreateClient.createSimple(zkcConnectString)
      val nodeCache = new NodeCache(zkc, znodePath)
      nodeCache.getListenable.addListener(new NodeCacheListener {
        @Override
        def nodeChanged = {
          try {
            val dataFromZNode = nodeCache.getCurrentData
            ProcessData(dataFromZNode, UpdOnLepMetadataCallback)
          } catch {
            case ex: Exception => {
              logger.error("Exception while fetching properties from zookeeper ZNode, reason " + ex.getCause())
            }
          }
        }
      })
      nodeCache.start
      logger.setLevel(Level.TRACE);
    } catch {
      case e: Exception => {
        throw new Exception("Failed to start a zookeeper session with(" + zkcConnectString + "): " + e.getMessage())
      }
    }
  }

  def StartLocalListener = {
    try {
      val znodePath = "/ligadata/metadata"
      val zkcConnectString = "localhost:2181"
      JsonSerializer.SetLoggerLevel(Level.TRACE)
      CreateNodeIfNotExists(zkcConnectString, znodePath)
      CreateListener(zkcConnectString, znodePath, null)

      breakable {
        for (ln <- io.Source.stdin.getLines) { // Exit after getting input from console
          if (zkc != null)
            zkc.close
          zkc = null
          println("Exiting")
          break
        }
      }
    } catch {
      case e: Exception => {
        throw new Exception("Failed to start a zookeeper session: " + e.getMessage())
      }
    } finally {
      if (zkc != null) {
        zkc.close()
      }
    }
  }

  private def PrintUsage(): Unit = {
    logger.warn("    --config <configfilename>")
  }

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--config" :: value :: tail =>
        nextOption(map ++ Map('config -> value), tail)
      case option :: tail => {
        logger.error("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  def main(args: Array[String]) = {
    var databaseOpen = false
    var jsonConfigFile = System.getenv("HOME") + "/MetadataAPIConfig.json"
    if (args.length == 0) {
      logger.error("Config File defaults to " + jsonConfigFile)
      logger.error("One Could optionally pass a config file as a command line argument:  --config myConfig.json")
      logger.error("The config file supplied is a complete path name of a  json file similar to one in github/RTD/trunk/MetadataAPI/src/main/resources/MetadataAPIConfig.json")
    }
    else{
      val options = nextOption(Map(), args.toList)
      val cfgfile = options.getOrElse('config, null)
      if (cfgfile == null) {
	logger.error("Need configuration file as parameter")
	throw new MissingArgumentException("Usage: configFile  supplied as --config myConfig.json")
      }
      jsonConfigFile = cfgfile.asInstanceOf[String]
    }
    try {
      MetadataAPIImpl.SetLoggerLevel(Level.TRACE)
      MdMgr.GetMdMgr.SetLoggerLevel(Level.TRACE)
      JsonSerializer.SetLoggerLevel(Level.TRACE)
      MetadataAPIImpl.InitMdMgrFromBootStrap(jsonConfigFile)
      databaseOpen = true
      StartLocalListener
    } catch {
      case e: Exception => {
        throw new Exception("Failed to start a zookeeper session: " + e.getMessage())
      }
    } finally {
      if( databaseOpen ){
	MetadataAPIImpl.CloseDbStore
      }
      if (zkc != null) {
        zkc.close()
      }
    }
  }
}
