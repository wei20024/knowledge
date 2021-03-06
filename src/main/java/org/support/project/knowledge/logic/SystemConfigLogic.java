package org.support.project.knowledge.logic;

import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.di.Container;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.config.SystemConfig;
import org.support.project.web.dao.SystemConfigsDao;
import org.support.project.web.entity.SystemConfigsEntity;

public class SystemConfigLogic {
	/** ログ */
	private static Log LOG = LogFactory.getLog(SystemConfigLogic.class);

	public static SystemConfigLogic get() {
		return Container.getComp(SystemConfigLogic.class);
	}
	
	/**
	 * ユーザ自身の手でユーザ追加のリクエストを出せるかチェック
	 * @return
	 */
	public boolean isUserAddAble() {
		SystemConfigsDao systemConfigsDao = SystemConfigsDao.get();
		SystemConfigsEntity userAddType = systemConfigsDao.selectOnKey(SystemConfig.USER_ADD_TYPE, AppConfig.SYSTEM_NAME);
		if (userAddType == null) {
			return false;
		}
		if (userAddType.getConfigValue().equals(SystemConfig.USER_ADD_TYPE_VALUE_ADMIN)) {
			return false;
		}
		return true;
	}
	
	
	
}
