package com.danzzan.infra.octomo;

public interface OctomoMessageClient {

    boolean existsRecentMessage(String mobileNum, String text);
}
