/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package bftsmart.reconfiguration.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;

/**
 *
 */
public class Configuration {
    
    protected int processId;
    protected boolean channelsBlocking;
    protected BigInteger DH_P;
    protected BigInteger DH_G;
    protected int autoConnectLimit;
    protected Map<String, String> configs;
    protected HostsConfig hosts;
           
    private String hmacAlgorithm = "HmacSha1";
    private int hmacSize = 160;

    protected static String configHome = "";

   
    protected static String hostsFileName = "";


    public Configuration(int procId){
        processId = procId;
        init();
    }
    
    public Configuration(int processId, String configHomeParam){
        this.processId = processId;
        configHome = configHomeParam;
        init();
    }

     public Configuration(int processId, String configHomeParam, String hostsFileNameParam){
        this.processId = processId;
        configHome = configHomeParam;
        hostsFileName = hostsFileNameParam;
        init();
    }
    
    protected void init(){
        try{
            hosts = new HostsConfig(configHome, hostsFileName);
            
                    
            loadConfig();
            
            String s = (String) configs.remove("system.autoconnect");
            if(s == null){
                autoConnectLimit = -1;
            }else{
                autoConnectLimit = Integer.parseInt(s);
            }

            s = (String) configs.remove("system.channels.blocking");
            if(s == null){
                channelsBlocking = false;
            }else{
                channelsBlocking = (s.equalsIgnoreCase("true"))?true:false;
            }
        }catch(Exception e){
            System.err.println("Wrong system.config file format.");
            e.printStackTrace(System.out);
        }
    }
     
    public final boolean isHostSetted(int id){
        if(hosts.getHost(id) == null){
            return false;
        }
        return true;
    }
    
    
    public final boolean useBlockingChannels(){
        return this.channelsBlocking;
    }
    
    public final int getAutoConnectLimit(){
        return this.autoConnectLimit;
    }
    
    public final BigInteger getDHP(){
        return DH_P;
    }

    public final BigInteger getDHG(){
        return DH_G;
    }
    
    public final String getHmacAlgorithm() {
        return hmacAlgorithm;
    }

    public final int getHmacSize() {
        return hmacSize;
    }

    public final String getProperty(String key){
        Object o = configs.get(key);
        if( o != null){
            return o.toString();
        }
        return null;
    }
    
    public final Map<String, String> getProperties(){
        return configs;
    }
    
    public final InetSocketAddress getRemoteAddress(int id){
        return hosts.getRemoteAddress(id);
    }
    

    public final InetSocketAddress getServerToServerRemoteAddress(int id){
        return hosts.getServerToServerRemoteAddress(id);
    }

    
    public final InetSocketAddress getLocalAddress(int id){
        return hosts.getLocalAddress(id);
    }
    
    public final String getHost(int id){
        return hosts.getHost(id);
    }
    
    public final int getPort(int id){
        return hosts.getPort(id);
    }
    
     public final int getServerToServerPort(int id){
        return hosts.getServerToServerPort(id);
    }
    
    
    public final int getProcessId(){
        return processId;
    }

    public final void setProcessId(int processId){
        this.processId = processId;
    }
    
  
    public final void addHostInfo(int id, String host, int port){
        this.hosts.add(id,host,port);
    }
    
    private void loadConfig(){
        configs = new Hashtable<String, String>();
        try{
            if(configHome == null || configHome.equals("")){
                configHome="config";
            }
            String sep = System.getProperty("file.separator");
            String path =  configHome+sep+"system.config";;
            FileReader fr = new FileReader(path);
            BufferedReader rd = new BufferedReader(fr);
            String line = null;
            while((line = rd.readLine()) != null){
                if(!line.startsWith("#")){
                    StringTokenizer str = new StringTokenizer(line,"=");
                    if(str.countTokens() > 1){
                        configs.put(str.nextToken().trim(),str.nextToken().trim());
                    }
                }
            }
            fr.close();
            rd.close();
        }catch(Exception e){
            e.printStackTrace(System.out);
        }
    }
}
