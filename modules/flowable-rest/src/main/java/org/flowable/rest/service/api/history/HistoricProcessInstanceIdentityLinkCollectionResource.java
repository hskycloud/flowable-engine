/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.rest.service.api.history;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricIdentityLink;
import org.flowable.rest.service.api.RestResponseFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * @author Tijs Rademakers
 */
@RestController
@Api(tags = { "History" }, description = "Manage History", authorizations = { @Authorization(value = "basicAuth") })
public class HistoricProcessInstanceIdentityLinkCollectionResource {

    @Autowired
    protected RestResponseFactory restResponseFactory;

    @Autowired
    protected HistoryService historyService;

    @ApiOperation(value = "Get the identity links of a historic process instance", tags = { "History" }, notes = "")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Indicates request was successful and the identity links are returned", response = HistoricIdentityLinkResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Indicates the process instance could not be found..") })
    @RequestMapping(value = "/history/historic-process-instances/{processInstanceId}/identitylinks", method = RequestMethod.GET, produces = "application/json")
    public List<HistoricIdentityLinkResponse> getProcessIdentityLinks(@ApiParam(name = "processInstanceId") @PathVariable String processInstanceId, HttpServletRequest request) {

        List<HistoricIdentityLink> identityLinks = historyService.getHistoricIdentityLinksForProcessInstance(processInstanceId);

        if (identityLinks != null) {
            return restResponseFactory.createHistoricIdentityLinkResponseList(identityLinks);
        }

        return new ArrayList<HistoricIdentityLinkResponse>();
    }
}
