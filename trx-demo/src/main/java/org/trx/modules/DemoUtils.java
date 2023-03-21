package org.trx.modules;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.tron.utils.HttpClientUtils;
import org.tron.utils.TronUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于波场的java实现
 */
@Component
public class DemoUtils {

    private static final String shastaTestNetUrl = "https://api.shasta.trongrid.io";

    //主网
    private static final String mainNetUrl = "https://api.trongrid.io";

    private static String tronUrl = mainNetUrl;


    public static void main(String[] args) throws Throwable {
        // 创建地址
        createAddress();
        // 查询余额
        String queryAddress = "";

        getTrxBalance(queryAddress);

        getTrc20Balance("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", queryAddress);

        // 转出地址
        String ownerAddress = "";
        // 转出地址密钥
        String fromPrivateKey = "";
        // 接收地址
        String toAddress = "";
        // trx转账
        String trxHash = transferTrx(ownerAddress, fromPrivateKey, toAddress, BigDecimal.ONE);
        System.out.print("trx转账：" + trxHash);
        // usdt转账
        String uHash = transferTrc20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", ownerAddress, fromPrivateKey, toAddress, BigDecimal.ONE);
        System.out.print("usdt转账：" + uHash);
    }


    /***
     * 创建新地址
     **/
    public static void createAddress() {
        Map<String, String> map = TronUtils.createAddress();
        String address = TronUtils.toViewAddress(map.get("address"));
        String privateKey = map.get("privateKey");
        System.out.print("address：" + address);
        System.out.print("privateKey：" + privateKey);
    }

    /***
     * 获得地址trx余额
     **/
    public static void getTrxBalance(String queryAddress) throws Exception {
        // 精度
        BigDecimal decimal = new BigDecimal("1000000");
        String url = tronUrl + "/wallet/getaccount";
        JSONObject param = new JSONObject();
        param.put("address", TronUtils.toHexAddress(queryAddress));
        String result = HttpClientUtils.postJson(url, param.toJSONString());
        BigInteger balance = BigInteger.ZERO;
        if (!StringUtils.isEmpty(result)) {
            JSONObject obj = JSONObject.parseObject(result);
            BigInteger b = obj.getBigInteger("balance");
            if (b != null) {
                balance = b;
            }
        }
        System.out.println("trx:" + new BigDecimal(balance).divide(decimal, 6, RoundingMode.FLOOR));
    }

    /***
     * 获得某地址trc20代币余额
     **/
    public static void getTrc20Balance(String contractAddress, String queryAddress) throws Exception {
        // 合约精度
        BigDecimal decimal = new BigDecimal("1000000");
        String url = tronUrl + "/wallet/triggerconstantcontract";
        JSONObject param = new JSONObject();
        param.put("owner_address", TronUtils.toHexAddress(queryAddress));
        param.put("contract_address", TronUtils.toHexAddress(contractAddress));
        param.put("function_selector", "balanceOf(address)");
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Address(TronUtils.toHexAddress(queryAddress).substring(2)));
        param.put("parameter", FunctionEncoder.encodeConstructor(inputParameters));
        String result = HttpClientUtils.postJson(url, param.toJSONString());
        BigDecimal amount = BigDecimal.ZERO;
        if (!StringUtils.isEmpty(result)) {
            JSONObject obj = JSONObject.parseObject(result);
            JSONArray results = obj.getJSONArray("constant_result");
            if (results != null && results.size() > 0) {
                BigInteger _amount = new BigInteger(results.getString(0), 16);
                amount = new BigDecimal(_amount).divide(decimal, 6, RoundingMode.FLOOR);
            }
        }
        System.out.printf("账号%s的balance=%s%n", queryAddress, amount);
    }

    /**
     * trc20转账
     */
    public static String transferTrc20(String contractAddress, String ownerAddress, String fromPrivateKey, String toAddress, BigDecimal amount) throws Throwable {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("contract_address", TronUtils.toHexAddress(contractAddress));
        jsonObject.put("function_selector", "transfer(address,uint256)");
        List<Type> inputParameters = new ArrayList<>();
        inputParameters.add(new Address(TronUtils.toHexAddress(toAddress).substring(2)));
        inputParameters.add(new Uint256(amount.multiply(BigDecimal.TEN.pow(6)).toBigInteger()));
        String parameter = FunctionEncoder.encodeConstructor(inputParameters);
        jsonObject.put("parameter", parameter);
        jsonObject.put("owner_address", TronUtils.toHexAddress(ownerAddress));
        jsonObject.put("call_value", 0);
        jsonObject.put("fee_limit", 100000000L);
        String trans1 = HttpClientUtils.postJson(tronUrl + "/wallet/triggersmartcontract", jsonObject.toString());
        JSONObject result = JSONObject.parseObject(trans1);
        if (result.containsKey("Error")) {
            System.out.println("send error==========");
            return "";
        }
        JSONObject tx = result.getJSONObject("transaction");
        String txid = TronUtils.signAndBroadcast(tronUrl, fromPrivateKey, tx);
        if (txid != null) {
            return txid;
        } else {
            return "";
        }
    }

    /***
     * trx转账
     **/
    public static String transferTrx(String ownerAddress, String fromPrivateKey, String toAddress, BigDecimal trxAmount) throws Throwable {
        String url = tronUrl + "/wallet/createtransaction";
        JSONObject param = new JSONObject();
        param.put("owner_address", TronUtils.toHexAddress(ownerAddress));
        param.put("to_address", TronUtils.toHexAddress(toAddress));
        // 将金额转为sun单位
        param.put("amount", trxAmount.multiply(BigDecimal.TEN.pow(6)).toBigInteger());
        String _result = HttpClientUtils.postJson(url, param.toJSONString());
        if (!StringUtils.isEmpty(_result)) {
            JSONObject transaction = JSONObject.parseObject(_result);
            return TronUtils.signAndBroadcast(tronUrl, fromPrivateKey, transaction);
        }
        return "";
    }


}
