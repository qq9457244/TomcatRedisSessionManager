package com.leefine.tomcat.redis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.io.*;
import java.util.*;


/**
 * Tomcat clustering with Redis data-cache implementation.
 * Redis data-cache implementation to store/retrieve session objects.
 */
public class RedisDataCache implements DataCache {

    private static DataCache dataCache;
    private Log log = LogFactory.getLog(RedisDataCache.class);

    public RedisDataCache() {
        initialize();
    }

    @Override
    public byte[] set(String key, byte[] value) {
        return dataCache.set(key, value);
    }

    @Override
    public Long setnx(String key, byte[] value) {
        return dataCache.setnx(key, value);
    }

    @Override
    public Long expire(String key, int seconds) {
        return dataCache.expire(key, seconds);
    }

    @Override
    public byte[] get(String key) {
        return (key != null) ? dataCache.get(key) : null;
    }

    @Override
    public Long delete(String key) {
        return dataCache.delete(key);
    }

    public static String parseDataCacheKey(String key) {
        return key.replaceAll("\\s", "_");
    }

    @SuppressWarnings("unchecked")
    private void initialize() {
        if (dataCache != null) {
            return;
        }
        Properties properties = loadProperties();

        boolean clusterEnabled = Boolean.valueOf(properties.getProperty(Constants.CLUSTER_ENABLED, Constants.DEFAULT_CLUSTER_ENABLED));

        String hosts = properties.getProperty(Constants.HOSTS, Protocol.DEFAULT_HOST.concat(":").concat(String.valueOf(Protocol.DEFAULT_PORT)));
        Collection<? extends Serializable> nodes = getJedisNodes(hosts, clusterEnabled);

        String password = properties.getProperty(Constants.PASSWORD);
        password = (password != null && !password.isEmpty()) ? password : null;

        int database = Integer.parseInt(properties.getProperty(Constants.DATABASE, String.valueOf(Protocol.DEFAULT_DATABASE)));

        int timeout = Integer.parseInt(properties.getProperty(Constants.TIMEOUT, String.valueOf(Protocol.DEFAULT_TIMEOUT)));
        timeout = (timeout < Protocol.DEFAULT_TIMEOUT) ? Protocol.DEFAULT_TIMEOUT : timeout;

        if (clusterEnabled) {
            if (password != null) {
                dataCache = new RedisClusterCacheUtil((Set<HostAndPort>) nodes, timeout, getPoolConfig(properties));
            } else {
                int maxAttempts = Integer.parseInt(properties.getProperty(Constants.MAXATTEMPTS, Constants.MAXATTEMPTS_VALUE));
                dataCache = new RedisClusterCacheUtil((Set<HostAndPort>) nodes, timeout, timeout, maxAttempts, password, getPoolConfig(properties));
            }
        } else {
            dataCache = new RedisCacheUtil(((List<String>) nodes).get(0),
                    Integer.parseInt(((List<String>) nodes).get(1)), password, database, timeout, getPoolConfig(properties));
        }
    }

    private JedisPoolConfig getPoolConfig(Properties properties) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        int maxActive = Integer.parseInt(properties.getProperty(Constants.MAX_ACTIVE, Constants.DEFAULT_MAX_ACTIVE_VALUE));
        poolConfig.setMaxTotal(maxActive);

        boolean testOnBorrow = Boolean.parseBoolean(properties.getProperty(Constants.TEST_ONBORROW, Constants.DEFAULT_TEST_ONBORROW_VALUE));
        poolConfig.setTestOnBorrow(testOnBorrow);

        boolean testOnReturn = Boolean.parseBoolean(properties.getProperty(Constants.TEST_ONRETURN, Constants.DEFAULT_TEST_ONRETURN_VALUE));
        poolConfig.setTestOnReturn(testOnReturn);

        int maxIdle = Integer.parseInt(properties.getProperty(Constants.MAX_ACTIVE, Constants.DEFAULT_MAX_ACTIVE_VALUE));
        poolConfig.setMaxIdle(maxIdle);

        int minIdle = Integer.parseInt(properties.getProperty(Constants.MIN_IDLE, Constants.DEFAULT_MIN_IDLE_VALUE));
        poolConfig.setMinIdle(minIdle);

        boolean testWhileIdle = Boolean.parseBoolean(properties.getProperty(Constants.TEST_WHILEIDLE, Constants.DEFAULT_TEST_WHILEIDLE_VALUE));
        poolConfig.setTestWhileIdle(testWhileIdle);

        int testNumPerEviction = Integer.parseInt(properties.getProperty(Constants.TEST_NUMPEREVICTION, Constants.DEFAULT_TEST_NUMPEREVICTION_VALUE));
        poolConfig.setNumTestsPerEvictionRun(testNumPerEviction);

        long timeBetweenEviction = Long.parseLong(properties.getProperty(Constants.TIME_BETWEENEVICTION, Constants.DEFAULT_TIME_BETWEENEVICTION_VALUE));
        poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEviction);
        return poolConfig;
    }

    private Collection<? extends Serializable> getJedisNodes(String hosts, boolean clusterEnabled) {
        hosts = hosts.replaceAll("\\s", "");
        String[] hostPorts = hosts.split(",");

        List<String> node = null;
        Set<HostAndPort> nodes = null;

        for (String hostPort : hostPorts) {
            String[] hostPortArr = hostPort.split(":");

            if (clusterEnabled) {
                nodes = (nodes == null) ? new HashSet<HostAndPort>() : nodes;
                nodes.add(new HostAndPort(hostPortArr[0], Integer.valueOf(hostPortArr[1])));
            } else {
                int port = Integer.valueOf(hostPortArr[1]);
                if (!hostPortArr[0].isEmpty() && port > 0) {
                    node = (node == null) ? new ArrayList<String>() : node;
                    node.add(hostPortArr[0]);
                    node.add(String.valueOf(port));
                    break;
                }
            }
        }
        return clusterEnabled ? nodes : node;
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        try {
            String filePath = System.getProperty(Constants.CATALINA_BASE).concat(File.separator)
                    .concat(Constants.CONF).concat(File.separator).concat(Constants.PROPERTIES_FILE);

            InputStream resourceStream = null;
            try {
                resourceStream = (filePath != null && !filePath.isEmpty() && new File(filePath).exists())
                        ? new FileInputStream(filePath) : null;

                if (resourceStream == null) {
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    resourceStream = loader.getResourceAsStream(Constants.PROPERTIES_FILE);
                }
                properties.load(resourceStream);
            } finally {
                resourceStream.close();
            }
        } catch (IOException ex) {
            log.error("Error while loading task scheduler properties", ex);
        }
        return properties;
    }
}
