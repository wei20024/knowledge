package org.support.project.knowledge.logic;

import java.util.ArrayList;
import java.util.List;

import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.di.Container;
import org.support.project.knowledge.dao.TagsDao;
import org.support.project.knowledge.entity.TagsEntity;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.entity.GroupsEntity;

public class TagLogic {
	/** ログ */
	private static Log LOG = LogFactory.getLog(TagLogic.class);

	public static TagLogic get() {
		return Container.getComp(TagLogic.class);
	}
	
	private TagsDao tagsDao = TagsDao.get();
	
	/**
	 * タグの一覧を取得
	 * 同時に、ナレッジの件数も取得する
	 * 件数は、ログインユーザがアクセス権があるもの
	 * @param loginedUser
	 * @param i
	 * @param pageLimit
	 * @return
	 */
	public List<TagsEntity> selectTagsWithCount(LoginedUser loginedUser, int offset, int limit) {
		int userid = Integer.MIN_VALUE;
		if (loginedUser != null) {
			userid = loginedUser.getUserId();
		}
		List<GroupsEntity> groups = new ArrayList<GroupsEntity>();
		if (loginedUser != null && loginedUser.getGroups() != null) {
			groups = loginedUser.getGroups();
		}
		
		TagsDao tagsDao = TagsDao.get();
		List<TagsEntity> tags;
		if (loginedUser != null && loginedUser.isAdmin()) {
			tags = tagsDao.selectWithKnowledgeCountAdmin(offset, limit);
		} else {
			tags = tagsDao.selectWithKnowledgeCount(userid, groups, offset * offset, limit);
		}
		return tags;
	}
	
	
	
	
	
}
